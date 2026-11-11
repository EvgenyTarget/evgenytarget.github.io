package kg.evgeny.moidengi;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.net.Uri;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.common.api.Scope;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Secure bridge between bundled finance UI and Google Sheets API v4.
 * Uses Google Identity Services AuthorizationClient: access tokens stay in
 * Android volatile process memory; no OAuth keys, passwords or tokens in JS.
 * Requires ONE Android OAuth client registered for package name and APK SHA-1.
 * This application does not read or write expenses to GitHub.
 */
public final class SheetsGateway {
    private static final int AUTH_REQUEST = 48031;
    private static final String DOC_ID = "16KltWbH23BaiV226AEzr1DnYKBFwysTFvbtA3N_DARo";
    private static final String BASE = "https://sheets.googleapis.com/v4/spreadsheets/" + DOC_ID;
    private static final String SCOPE = "https://www.googleapis.com/auth/spreadsheets";
    private static final int EXPENSE_SHEET_ID = 702732328;
    private static final int BUY_SHEET_ID = 912300114;
    private final Activity activity;
    private final WebView webView;
    private final AuthorizationClient auth;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayDeque<JSONObject> queue = new ArrayDeque<>();
    private String token;
    private boolean authorizing = false;
    private boolean closed = false;

    SheetsGateway(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        auth = Identity.getAuthorizationClient(activity);
    }

    @JavascriptInterface public void request(String input) {
        if (input == null || input.length() > 250000) return;
        try {
            JSONObject msg = new JSONObject(input);
            final String id = msg.getString("id");
            if (!id.matches("s[0-9]{1,14}")) return;
            activity.runOnUiThread(() -> {
                if (closed) return;
                queue.add(msg);
                if (token != null) drain(); else authorize();
            });
        } catch (Exception ignored) { }
    }

    private void authorize() {
        if (authorizing || closed) return;
        authorizing = true;
        AuthorizationRequest request = AuthorizationRequest.builder()
            .setRequestedScopes(Arrays.asList(new Scope(SCOPE)))
            .build();
        auth.authorize(request)
            .addOnSuccessListener(activity, this::authorized)
            .addOnFailureListener(activity, e -> authFailure(e.getMessage()));
    }

    private void authorized(AuthorizationResult result) {
        if (closed) return;
        if (result.hasResolution()) {
            try {
                PendingIntent pi=result.getPendingIntent();
                activity.startIntentSenderForResult(pi.getIntentSender(),AUTH_REQUEST,null,0,0,0);
            } catch (Exception e) { authFailure("Не удалось показать выбор аккаунта Google"); }
            return;
        }
        if (result.getAccessToken() == null || result.getAccessToken().isEmpty()) {
            authFailure("Google не выдал разрешение на таблицу");return;
        }
        token=result.getAccessToken();
        authorizing=false;
        drain();
    }

    boolean onAuthResult(int code,int resultCode,Intent data) {
        if (code != AUTH_REQUEST) return false;
        if (resultCode != Activity.RESULT_OK || data == null) {
            authFailure("Вход в Google отменён"); return true;
        }
        try {
            authorized(auth.getAuthorizationResultFromIntent(data));
        } catch(Exception e) { authFailure("Google OAuth не настроен для подписи приложения или доступ отклонён: "+e.getMessage()); }
        return true;
    }

    private void authFailure(String message) {
        authorizing=false;
        token=null;
        while (!queue.isEmpty()) reply(queue.remove(),false,
            "Google не подключён. Требуется одноразовая настройка OAuth-клиента Android для этого APK. "+String.valueOf(message));
    }

    private void drain() {
        if (closed || token == null) return;
        while (!queue.isEmpty()) {
            JSONObject job=queue.remove();
            String bearer=token;
            io.execute(() -> {
                try {
                    JSONObject request=job;
                    String method=request.getString("method");
                    JSONArray args=request.optJSONArray("args");
                    if (args == null) args=new JSONArray();
                    Object output=dispatch(method,args,bearer);
                    reply(job,true,output.toString());
                } catch(Exception e) {
                    String msg=e.getMessage()==null?"Ошибка Google Sheets API":e.getMessage();
                    if (msg.startsWith("HTTP 401")) {
                        activity.runOnUiThread(() -> token=null);
                        msg="Сессия Google истекла. Нажми «Обновить» для повторного входа";
                    }
                    reply(job,false,msg);
                }
            });
        }
    }

    private void reply(JSONObject request, boolean ok, String payload) {
        String id=request.optString("id");
        final String js="window.__nativeSheetsReply("+JSONObject.quote(id)+","+
            (ok?"true":"false")+","+JSONObject.quote(payload)+");";
        activity.runOnUiThread(() -> {
            if(!closed)webView.evaluateJavascript(js,null);
        });
    }

    private Object dispatch(String method,JSONArray args,String bearer) throws Exception {
        switch(method) {
            case "readAll": return readExpenses(bearer);
            case "readShoppingList": return readShopping(bearer);
            case "getFxRates": return getRates();
            case "addExpense": return addExpenses(new JSONArray().put(args.getJSONObject(0)),bearer);
            case "addExpensesBatch": return addExpenses(args.getJSONArray(0),bearer);
            case "replaceExpense": return replaceExpense(args.getJSONObject(0),bearer);
            case "removeExpense": return remove(args.getString(0),"Расходы",EXPENSE_SHEET_ID,bearer);
            case "addShoppingItem": return addShopping(new JSONArray().put(args.getJSONObject(0)),bearer);
            case "addShoppingItemsBatch": return addShopping(args.getJSONArray(0),bearer);
            case "updateShoppingItem": return updateShopping(args.getJSONObject(0),bearer);
            case "deleteShoppingItem": return remove(args.getString(0),"Список покупок",BUY_SHEET_ID,bearer);
            case "setShoppingDone": return toggleShopping(args.getString(0),args.getBoolean(1),bearer);
            default: throw new IllegalArgumentException("Неизвестная операция");
        }
    }

    private static String str(JSONArray values,int index) {
        Object value=values.opt(index);
        return value == null || value == JSONObject.NULL?"":String.valueOf(value);
    }
    private static double number(JSONArray row,int i) {
        return row.optDouble(i,0);
    }
    private static JSONArray cells(JSONArray source,int count) {
        JSONArray row=new JSONArray();
        for(int i=0;i<count;i++)row.put(i<source.length()?source.opt(i):"");
        return row;
    }
    private static String stamp() {
        SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Bishkek"));
        return sdf.format(new Date());
    }
    private static String id(JSONObject obj,String prefix){
        String value=obj.optString("id","");
        if(!value.matches(prefix+"-[A-Za-z0-9_-]{8,90}")) throw new IllegalArgumentException("Неверный ID");
        return value;
    }
    private static void nonempty(String value,String field) {
        if(value==null || value.trim().isEmpty())throw new IllegalArgumentException("Заполни "+field);
    }
    private JSONArray expenseRow(JSONObject obj) throws Exception {
        String key=id(obj,"exp"),description=obj.optString("description","").trim();
        nonempty(description,"описание");
        String date=obj.optString("date","");
        if(!date.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException("Неверная дата");
        String currency=obj.optString("currency","");
        if(!Arrays.asList("KGS","USD","VND","KZT").contains(currency))
            throw new IllegalArgumentException("Неизвестная валюта");
        double original=obj.optDouble("amount",0),kgs=obj.optDouble("kgs",0);
        if(!Double.isFinite(original)||!Double.isFinite(kgs)||original<=0||kgs<=0||
            original>1e12||kgs>1e12||(currency.equals("KGS")&&Math.abs(kgs-original)>.01))
            throw new IllegalArgumentException("Проверь сумму");
        String type=obj.optString("type","Расход");
        if(!type.equals("Расход")&&!type.equals("Доход")) type="Расход";
        return new JSONArray().put(key).put(date).put(description)
            .put(obj.optString("category","Другое")).put(obj.optString("project","Семья"))
            .put(kgs).put(original).put(currency).put(type).put(obj.optString("note",""));
    }
    private JSONArray shoppingRow(JSONObject obj,JSONArray prior) throws Exception {
        String key=id(obj,"buy"),name=obj.optString("name","").trim();
        nonempty(name,"покупку");
        double qty=obj.optDouble("quantity",1);
        if(!Double.isFinite(qty)||qty<=0||qty>1e5)throw new IllegalArgumentException("Количество неверно");
        return new JSONArray().put(key).put(name).put(qty)
            .put(obj.optString("unit","шт.")).put(obj.optString("category","Продукты"))
            .put(obj.optString("priority","Обычный"))
            .put(prior!=null?str(prior,6):"Нужно купить")
            .put(prior!=null?str(prior,7):stamp())
            .put(prior!=null?str(prior,8):"")
            .put(obj.optString("note",""));
    }

    private String rangeUrl(String sheet,String range){
        return BASE+"/values/"+Uri.encode("'"+sheet+"'!"+range);
    }
    private JSONArray getRows(String sheet,String bearer) throws Exception {
        JSONObject j=call("GET",rangeUrl(sheet,"A1:J2000")+"?valueRenderOption=UNFORMATTED_VALUE",null,bearer);
        return j.optJSONArray("values")==null?new JSONArray():j.getJSONArray("values");
    }
    private JSONArray readExpenses(String bearer) throws Exception {
        JSONArray values=getRows("Расходы",bearer),result=new JSONArray();
        for(int i=1;i<values.length();i++){
            JSONArray row=values.optJSONArray(i);if(row==null||str(row,0).isEmpty())continue;
            result.put(new JSONObject().put("id",str(row,0)).put("date",str(row,1))
                .put("description",str(row,2)).put("category",str(row,3))
                .put("project",str(row,4)).put("kgs",number(row,5))
                .put("amount",number(row,6)).put("currency",str(row,7))
                .put("type",str(row,8)).put("note",str(row,9)));
        }
        return result;
    }
    private JSONArray readShopping(String bearer) throws Exception {
        JSONArray values=getRows("Список покупок",bearer),out=new JSONArray();
        for(int i=1;i<values.length();i++){
            JSONArray row=values.optJSONArray(i);if(row==null||str(row,0).isEmpty())continue;
            out.put(new JSONObject().put("id",str(row,0)).put("name",str(row,1))
                .put("quantity",number(row,2)).put("unit",str(row,3))
                .put("category",str(row,4)).put("priority",str(row,5))
                .put("done",str(row,6).equals("Куплено")).put("created",str(row,7))
                .put("completed",str(row,8)).put("note",str(row,9)));
        }
        return out;
    }
    private int findIndex(JSONArray rows,String id){
        for(int i=1;i<rows.length();i++){
            JSONArray row=rows.optJSONArray(i);
            if(row!=null&&str(row,0).equals(id))return i;
        }
        return -1;
    }
    private JSONObject append(String sheet,JSONArray values,String bearer) throws Exception {
        if(values.length()==0)return new JSONObject().put("ok",true).put("added",0);
        JSONObject payload=new JSONObject().put("majorDimension","ROWS").put("values",values);
        call("POST",rangeUrl(sheet,"A:J")+":append?valueInputOption=RAW&insertDataOption=INSERT_ROWS",payload,bearer);
        return new JSONObject().put("ok",true).put("added",values.length());
    }
    private JSONObject updateRow(String sheet,int index,JSONArray row,String bearer) throws Exception {
        // index 1 is spreadsheet row 2 (header is row 1)
        int sheetRow=index+1;
        call("PUT",rangeUrl(sheet,"A"+sheetRow+":J"+sheetRow)+"?valueInputOption=RAW",
            new JSONObject().put("values",new JSONArray().put(row)),bearer);
        return new JSONObject().put("ok",true);
    }
    private JSONObject addExpenses(JSONArray items,String bearer) throws Exception {
        if(items.length()==0||items.length()>100)throw new IllegalArgumentException("Допустимо 1–100 расходов");
        JSONArray existing=getRows("Расходы",bearer);
        Set<String> seen=new HashSet<>();
        for(int i=1;i<existing.length();i++){
            JSONArray row=existing.optJSONArray(i);
            if(row!=null)seen.add(str(row,0));
        }
        JSONArray missing=new JSONArray();
        for(int i=0;i<items.length();i++){
            JSONArray row=expenseRow(items.getJSONObject(i));
            if(seen.add(str(row,0)))missing.put(row);
        }
        return append("Расходы",missing,bearer);
    }
    private JSONObject replaceExpense(JSONObject item,String bearer) throws Exception {
        JSONArray existing=getRows("Расходы",bearer);
        int i=findIndex(existing,id(item,"exp"));
        if(i<0)throw new IllegalArgumentException("Расход не найден");
        return updateRow("Расходы",i,expenseRow(item),bearer);
    }
    private JSONObject addShopping(JSONArray items,String bearer) throws Exception {
        if(items.length()==0||items.length()>100)throw new IllegalArgumentException("Допустимо 1–100 покупок");
        JSONArray existing=getRows("Список покупок",bearer);
        Set<String> seen=new HashSet<>();
        for(int i=1;i<existing.length();i++){
            JSONArray row=existing.optJSONArray(i);if(row!=null)seen.add(str(row,0));
        }
        JSONArray missing=new JSONArray();
        for(int i=0;i<items.length();i++){
            JSONArray row=shoppingRow(items.getJSONObject(i),null);
            if(seen.add(str(row,0)))missing.put(row);
        }
        return append("Список покупок",missing,bearer);
    }
    private JSONObject updateShopping(JSONObject item,String bearer) throws Exception {
        JSONArray existing=getRows("Список покупок",bearer);
        int i=findIndex(existing,id(item,"buy"));
        if(i<0)throw new IllegalArgumentException("Покупка не найдена");
        return updateRow("Список покупок",i,shoppingRow(item,existing.getJSONArray(i)),bearer);
    }
    private JSONObject toggleShopping(String id,boolean done,String bearer) throws Exception {
        JSONArray existing=getRows("Список покупок",bearer);
        int i=findIndex(existing,id);
        if(i<0)throw new IllegalArgumentException("Покупка не найдена");
        JSONArray row=cells(existing.getJSONArray(i),10);
        row.put(6,done?"Куплено":"Нужно купить");
        row.put(8,done?stamp():"");
        return updateRow("Список покупок",i,row,bearer);
    }
    private JSONObject remove(String id,String sheet,int sheetId,String bearer) throws Exception {
        JSONArray existing=getRows(sheet,bearer);
        int i=findIndex(existing,id);
        if(i<0)throw new IllegalArgumentException("Запись не найдена");
        JSONObject dim=new JSONObject().put("sheetId",sheetId).put("dimension","ROWS")
            .put("startIndex",i).put("endIndex",i+1);
        JSONObject req=new JSONObject().put("requests",new JSONArray()
            .put(new JSONObject().put("deleteDimension",new JSONObject().put("range",dim))));
        call("POST",BASE+":batchUpdate",req,bearer);
        return new JSONObject().put("ok",true);
    }
    private JSONObject getRates() throws Exception {
        JSONObject json=call("GET","https://open.er-api.com/v6/latest/USD",null,null);
        if(!"success".equals(json.optString("result")))throw new IllegalStateException("Сервис валют недоступен");
        JSONObject all=json.getJSONObject("rates"),needed=new JSONObject().put("USD",1);
        for(String c:Arrays.asList("KGS","VND","KZT"))needed.put(c,all.getDouble(c));
        return new JSONObject().put("rates",needed).put("date",json.optString("time_last_update_utc",""));
    }
    private JSONObject call(String verb,String url,JSONObject body,String bearer) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod(verb);
        c.setConnectTimeout(10000);c.setReadTimeout(18000);
        c.setRequestProperty("Accept","application/json");
        if(bearer!=null)c.setRequestProperty("Authorization","Bearer "+bearer);
        if(body!=null){
            c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            c.setDoOutput(true);
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=c.getOutputStream()){out.write(bytes);}
        }
        int status=c.getResponseCode();
        try(InputStream in=status<400?c.getInputStream():c.getErrorStream()){
            ByteArrayOutputStream buf=new ByteArrayOutputStream();
            if(in!=null){
                byte[] bytes=new byte[8192];int n;
                while((n=in.read(bytes))!=-1) {
                    buf.write(bytes,0,n);
                    if(buf.size()>4000000)throw new IllegalStateException("Превышен размер ответа");
                }
            }
            String text=new String(buf.toByteArray(),StandardCharsets.UTF_8);
            if(status<200||status>=300) {
                String shortMsg="HTTP "+status;
                try {
                    JSONObject e=new JSONObject(text).optJSONObject("error");
                    if(e!=null)shortMsg+=": "+e.optString("message","").substring(0,Math.min(180,e.optString("message","").length()));
                } catch(Exception ignored){}
                throw new IllegalStateException(shortMsg);
            }
            return text.isEmpty()?new JSONObject():new JSONObject(text);
        } finally {c.disconnect();}
    }

    void close(){closed=true;queue.clear();token=null;io.shutdownNow();}
}

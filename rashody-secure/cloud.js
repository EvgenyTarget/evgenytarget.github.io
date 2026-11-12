
// The public GitHub feed is authenticated AES-GCM ciphertext only.
// The secret is inserted OFFLINE after CI builds the unsigned-data APK.
// Never commit the real secret or a decrypted finance entry.
const FINANCE_FEED='https://raw.githubusercontent.com/EvgenyTarget/evgenytarget.github.io/main/rashody/data-v3.enc.json';
const FINANCE_DECRYPTION_KEY='__MOI_DENGI_PRIVATE_BASE64URL_KEY__';
const FINANCE_CLOUD_CACHE='my-private-finance-cache-v3';
const base64Bytes=s=>{
  const raw=atob(String(s).replace(/-/g,'+').replace(/_/g,'/')+
    '='.repeat((4-String(s).length%4)%4));
  return Uint8Array.from(raw,c=>c.charCodeAt(0));
};
function applyCloudCache(){
  try{
    const content=JSON.parse(localStorage.getItem(FINANCE_CLOUD_CACHE)||'null');
    if(!content||content.format!=='moi-dengi-v3'||!Array.isArray(content.entries)||
      !Array.isArray(content.shopping))return;
    secureCloudIds=new Set(content.entries.filter(e=>e&&e.id).map(e=>e.id));
    secureShoppingIds=new Set(content.shopping.filter(e=>e&&e.id).map(e=>e.id));
    const own=rows.filter(e=>e&&!secureCloudIds.has(e.id));
    rows=own.concat(content.entries.filter(e=>e&&e.id));
    let overrides={};
    try{overrides=JSON.parse(localStorage.getItem('my-shopping-overrides-v1')||'{}')||{}}catch(e){}
    const ownedShopping=shopping.filter(e=>e&&!secureShoppingIds.has(e.id));
    const cloudShopping=content.shopping.filter(e=>e&&e.id).map(e=>{
      const done=Object.prototype.hasOwnProperty.call(overrides,e.id)?
        !!overrides[e.id]:!!e.done;
      return {...e,done};
    });
    shopping=ownedShopping.concat(cloudShopping);
    secureCloudReady=true;
  }catch(e){console.warn('Cached finance data unavailable',e)}
}
async function secureSync(){
  if(secureSyncing)return;
  secureSyncing=true;
  const syncButton=$('sync');
  if(syncButton)syncButton.disabled=true;
  try{
    if(!window.crypto||!window.crypto.subtle)throw Error('Шифрование недоступно в этом Android WebView');
    const ctrl=new AbortController();
    const timeout=setTimeout(()=>ctrl.abort(),12000);
    let reply;
    try{
      reply=await fetch(FINANCE_FEED+'?t='+Date.now(),{
        cache:'no-store',mode:'cors',signal:ctrl.signal,
        credentials:'omit',redirect:'error'
      });
    }finally{clearTimeout(timeout)}
    if(!reply.ok)throw Error('Сервер расходов вернул '+reply.status);
    const envelope=await reply.json();
    if(envelope.version!==3||envelope.alg!=='AES-256-GCM'||
      typeof envelope.iv!=='string'||typeof envelope.ciphertext!=='string')
      throw Error('Неверный формат защищённых данных');
    const secret=base64Bytes(FINANCE_DECRYPTION_KEY);
    if(secret.length!==32)throw Error('Неверный ключ локального приложения');
    const key=await crypto.subtle.importKey('raw',secret,{name:'AES-GCM'},false,['decrypt']);
    const plaintext=await crypto.subtle.decrypt(
      {name:'AES-GCM',iv:base64Bytes(envelope.iv)},key,base64Bytes(envelope.ciphertext));
    const result=JSON.parse(new TextDecoder().decode(plaintext));
    if(result.format!=='moi-dengi-v3'||!Array.isArray(result.entries)||
      !Array.isArray(result.shopping)||result.entries.length>5000||
      result.shopping.length>5000)throw Error('Проверка содержимого не пройдена');
    localStorage.setItem(FINANCE_CLOUD_CACHE,JSON.stringify(result));
    await reload();
    lastSync=new Date().toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit'});
    $('status').classList.remove('offline');
    $('status').textContent='Защищённые расходы обновлены · '+lastSync;
    connected=true;
    ping('Расходы обновлены');
  }catch(e){
    if(!secureCloudReady)await reload();
    $('status').classList.add('offline');
    $('status').textContent=secureCloudReady?
      'Нет связи · показаны сохранённые расходы':'Нет связи · облачные расходы недоступны';
    if(!secureCloudReady)ping('Не удалось загрузить расходы: '+(e.message||String(e)));
    console.warn('Finance sync unavailable',e);
  }finally{secureSyncing=false;if(syncButton)syncButton.disabled=false}
}
async function secureStart(){
  const section=$('secure-pair');if(section)section.hidden=true;
  await secureSync();
}

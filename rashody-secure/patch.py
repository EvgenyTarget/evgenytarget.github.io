#!/usr/bin/env python3
"""Prepare a generic Android finance APK, WITHOUT finance secrets.

Only the PRIVATE, locally delivered APK receives the AES-GCM decryption key.
No real finance data or keys are present in GitHub or GitHub Actions artifacts.
"""
from pathlib import Path
import shutil

source=Path("rashody-android")
target=Path("/tmp/moi-dengi-secure")
if target.exists(): shutil.rmtree(target)
shutil.copytree(source,target,ignore=shutil.ignore_patterns("downloads","build",".gradle"))
gradle=target/"app/build.gradle"
text=gradle.read_text()
assert "applicationId 'kg.evgeny.moidengi.sheets.next'" in text
text=text.replace("applicationId 'kg.evgeny.moidengi.sheets.next'",
                  "applicationId 'kg.evgeny.moidengi.secure'")
text=text.replace("versionCode 6","versionCode 1")
text=text.replace("versionName '2.2.0'","versionName '3.1.0'")
gradle.write_text(text)

activity=target/"app/src/main/java/kg/evgeny/moidengi/MainActivity.java"
text=activity.read_text()
auth='sheets=new SheetsGateway(this, webView);\n        webView.addJavascriptInterface(sheets, "SheetsBridge");'
assert auth in text
text=text.replace(auth,'// Google OAuth is intentionally not invoked in this encrypted-only edition.\n        sheets=null;')
original='&& "open.er-api.com".equals(uri.getHost())))'
assert original in text
text=text.replace(original,'''&& "open.er-api.com".equals(uri.getHost()) ||
                    "https".equals(uri.getScheme())
                    && "raw.githubusercontent.com".equals(uri.getHost())
                    && "/EvgenyTarget/evgenytarget.github.io/main/rashody/data.enc.json".equals(uri.getPath())))''')
# Keep one trusted external finance-feed endpoint and the currency-rate endpoint.
csp='connect-src \\'self\\' https://open.er-api.com; '
assert csp in text
text=text.replace(csp,"connect-src 'self' https://open.er-api.com https://raw.githubusercontent.com; ")
activity.write_text(text)

html=target/"app/src/main/assets/index.html"
text=html.read_text()
old='''// Direct Google Sheets API is the only remote finance backend in the APK.
function applyCloudCache(){}
async function secureSync(){}
async function secureStart(){const section=$('secure-pair');if(section)section.hidden=true}
'''
assert old in text
cloud=Path("rashody-secure/cloud.js").read_text()
assert "__MOI_DENGI_PRIVATE_BASE64URL_KEY__" in cloud
text=text.replace(old,cloud)
assert len(text)>60000
html.write_text(text)

script=target/"tools/smoke_test_android.sh"
s=script.read_text()
assert 'kg.evgeny.moidengi.sheets.next' in s
script.write_text(s.replace('kg.evgeny.moidengi.sheets.next','kg.evgeny.moidengi.secure')
    .replace('APK="rashody-android/app/build/outputs/apk/debug/app-debug.apk"',
        'APK="/tmp/moi-dengi-secure/app/build/outputs/apk/debug/app-debug.apk"'))
print("Prepared generic secure APK; real key only inserted after private download")

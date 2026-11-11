# «Мои деньги Онлайн» — Android launcher through Chrome Custom Tabs

This is a fallback **not a native Google Sheets bridge**. It launches the
**existing** private Google Apps Script web app in the user's normal
authenticated Chrome session, which was visibly working on 11 November 2026.
Unlike the broken v2.2, it does not use an Android Google OAuth client at all.

- Existing script /exec deployment: `https://script.google.com/macros/s/AKfycbxldHZKNboOWwoq8MJwzKUTHPEB86bniLdO4GAeSojWz9fmeMm7TIRA4glbTdUNz75NYA/exec`.
- No copied financial data, secret, password, bearer token, or Google API key.
- Does not modify the Google Sheet, existing v2.0, or v2.2.
- Package: `kg.evgeny.moidengi.portal`, installed alongside old apps.
- Browser toolbar appears; Google account must already be signed in to Chrome.
- Browser session, page rotation, and OAuth are managed by system Chrome.
- If Google Apps Script is deleted, inaccessible, or its own UI breaks,
  this launcher cannot repair it.
- For the owner: do not remove working v2.0 until expenses have been confirmed
  in the browser portal on the phone.

A public debug-signed APK is not a long-lived production release signature.
The hosted web UI can update without reissuing this launcher binary.

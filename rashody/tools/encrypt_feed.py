#!/usr/bin/env python3
"""Convert a private finance snapshot from stdin to a P-256 encrypted JSON feed.

Usage: python encrypt_feed.py --key device-public-key.json < private-snapshot.json > data.enc.json
Never commit private-snapshot.json. Only publish the encrypted output.
"""
import argparse, base64, json, re, sys
from datetime import date
from decimal import Decimal
from pathlib import Path
from secrets import token_bytes
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

def b64(b):
    return base64.urlsafe_b64encode(b).decode().rstrip("=")

def unb64(s):
    if not isinstance(s,str) or not re.fullmatch(r"[A-Za-z0-9_-]+",s):
        raise ValueError("Invalid public-key encoding")
    return base64.urlsafe_b64decode(s+"="*(-len(s)%4))

def amount(x,label):
    if isinstance(x,bool) or not isinstance(x,(int,float,str)):
        raise ValueError("Invalid "+label)
    try: d=Decimal(str(x))
    except Exception as exc: raise ValueError("Invalid "+label) from exc
    if not d.is_finite() or d<=0 or d>1000000000000:
        raise ValueError("Invalid "+label)
    return d

def validate(data):
    if not isinstance(data,dict) or data.get("format")!="moi-dengi-v1":
        raise ValueError("Wrong snapshot format")
    for k in ("entries","shopping"):
        if not isinstance(data.get(k),list) or len(data[k])>10000:
            raise ValueError("Invalid "+k)
    ids=set()
    for r in data["entries"]:
        if not isinstance(r,dict) or not isinstance(r.get("id"),str) or not r["id"] or r["id"] in ids:
            raise ValueError("Missing or duplicate expense ID")
        ids.add(r["id"])
        date.fromisoformat(r["date"])
        if not isinstance(r.get("description"),str) or not r["description"].strip():
            raise ValueError("Missing description")
        if r.get("currency") not in ("KGS","USD","VND","KZT"):
            raise ValueError("Unknown currency")
        kgs,original=amount(r.get("kgs"),"kgs"),amount(r.get("amount"),"amount")
        if r["currency"]=="KGS" and abs(kgs-original)>Decimal(".01"):
            raise ValueError("KGS mismatch")
        if r.get("type") not in ("Расход","Доход","Возврат"):
            raise ValueError("Unknown transaction type")
    ids=set()
    for x in data["shopping"]:
        if not isinstance(x,dict) or not isinstance(x.get("id"),str) or not x["id"] or x["id"] in ids:
            raise ValueError("Missing or duplicate shopping ID")
        ids.add(x["id"])
        if not isinstance(x.get("name"),str) or not x["name"].strip():
            raise ValueError("Missing item name")
        amount(x.get("quantity"),"quantity")
        if not isinstance(x.get("done"),bool):
            raise ValueError("Invalid purchased status")

def encrypt(data,pubkey):
    validate(data)
    target=serialization.load_der_public_key(unb64(pubkey))
    if not isinstance(target,ec.EllipticCurvePublicKey) or target.curve.name!="secp256r1":
        raise ValueError("Public key must be P-256")
    eph=ec.generate_private_key(ec.SECP256R1())
    salt,iv=token_bytes(16),token_bytes(12)
    shared=eph.exchange(ec.ECDH(),target)
    key=HKDF(algorithm=hashes.SHA256(),length=32,salt=salt,info=b"moi-dengi-feed-v1").derive(shared)
    raw=json.dumps(data,ensure_ascii=False,separators=(",",":"),allow_nan=False).encode()
    cipher=AESGCM(key).encrypt(iv,raw,b"moi-dengi-v1")
    epk=eph.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
    return {"version":1,"alg":"P256-HKDF-SHA256-AES256GCM","ephemeral":b64(epk),"salt":b64(salt),"iv":b64(iv),"ciphertext":b64(cipher)}

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--key",type=Path,required=True,help="JSON containing device publicKey")
    args=parser.parse_args()
    public=json.loads(args.key.read_text(encoding="utf-8"))["publicKey"]
    snapshot=json.load(sys.stdin)
    print(json.dumps(encrypt(snapshot,public),separators=(",",":")))

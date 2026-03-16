import pexpect
import sys
import time
import os
import subprocess

def kill_process_on_port(port):
    try:
        # get PID using pgrep matching command specific to tunnel
        cmd = f"pgrep -f 'ssh.*:{port}'"
        pids = subprocess.check_output(cmd, shell=True).decode().strip().split()
        for pid in pids:
            if pid:
                print(f"Killing process {pid}")
                os.kill(int(pid), 9)
                time.sleep(1)
    except subprocess.CalledProcessError:
        print(f"No process found matching tunnel pattern for port {port}")
    except Exception as e:
        print(f"Error killing process: {e}")

def start_tunnel():
    kill_process_on_port(4001)

    print("Starting SSH tunnel on 142.1.46.107:4001...")
    # Bind to 142.1.46.107 explicitly for external access
    cmd = "ssh -p 2222 -o StrictHostKeyChecking=no -f -N -L 142.1.46.107:4001:localhost:4001 student@localhost"
    
    child = pexpect.spawn(cmd)
    
    try:
        i = child.expect(['password:', pexpect.EOF, pexpect.TIMEOUT], timeout=10)
        if i == 0:
            child.sendline("hhhhiotwwg!!")
            child.expect(pexpect.EOF, timeout=10)
            print("Tunnel started successfully.")
        elif i == 1:
            print("SSH exited immediately.")
            print(child.before.decode())
        else:
            print("Timeout waiting for password prompt.")
    except Exception as e:
        print(f"Error starting tunnel: {e}")

if __name__ == "__main__":
    start_tunnel()




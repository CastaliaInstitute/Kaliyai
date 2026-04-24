#!/system/bin/sh
# Run on phone: su 0 sh /sdcard/install-kali-linux-large.sh
# Log: /sdcard/nh-kali-linux-large.log
export DEBIAN_FRONTEND=noninteractive
export PATH=/data/data/com.offsec.nethunter/files/bin:/sbin:/usr/sbin:/bin:/usr/bin
exec >>/sdcard/nh-kali-linux-large.log 2>&1
echo "=== start $(date) ==="
/data/data/com.offsec.nethunter/scripts/bootkali custom_cmd apt update
rc1=$?
/data/data/com.offsec.nethunter/scripts/bootkali custom_cmd apt install -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold kali-linux-large
rc2=$?
echo "=== end $(date) update=$rc1 install=$rc2 ==="

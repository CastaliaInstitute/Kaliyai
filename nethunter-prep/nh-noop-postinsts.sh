#!/system/bin/sh
# Run on device: su 0 sh /sdcard/nh-noop-postinsts.sh
D=/data/local/nhsystem/kali-armhf/var/lib/dpkg/info
for p in udev dhcpcd-base cron-daemon-common openssh-client openssh-server openssh-sftp-server cron kali-nethunter-core; do
  f="$D/${p}.postinst"
  if [ -f "$f" ] && [ ! -f "$f.nh_bak" ]; then cp -a "$f" "$f.nh_bak"; fi
  if [ -f "$f" ]; then
    cat /sdcard/stub-sh.sh > "$f"
    chmod 755 "$f"
  fi
done
echo done

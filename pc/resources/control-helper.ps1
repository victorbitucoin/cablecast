# CableCast control helper — injects mouse/keyboard events sent as JSON lines on stdin.
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class CCInput {
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern bool GetCursorPos(out POINT p);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint data, UIntPtr extra);
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
  public struct POINT { public int X; public int Y; }
}
"@

$VK = @{
  'enter'=0x0D;'esc'=0x1B;'backspace'=0x08;'tab'=0x09;'space'=0x20;'shift'=0x10;'ctrl'=0x11;'alt'=0x12;
  'left'=0x25;'up'=0x26;'right'=0x27;'down'=0x28;'win'=0x5B;'delete'=0x2E;'home'=0x24;'end'=0x23;
  'pageup'=0x21;'pagedown'=0x22;'volup'=0xAF;'voldown'=0xAE;'volmute'=0xAD;'playpause'=0xB3;
  'f5'=0x74;'f11'=0x7A
}
$sh = New-Object -ComObject WScript.Shell

while ($true) {
  $line = [Console]::In.ReadLine()
  if ($null -eq $line) { break }
  try {
    $m = $line | ConvertFrom-Json
    switch ($m.type) {
      'mouse' {
        $p = New-Object CCInput+POINT
        [CCInput]::GetCursorPos([ref]$p) | Out-Null
        [CCInput]::SetCursorPos($p.X + [int]$m.dx, $p.Y + [int]$m.dy) | Out-Null
      }
      'click' {
        if ($m.button -eq 'right') { [CCInput]::mouse_event(0x0008,0,0,0,[UIntPtr]::Zero); [CCInput]::mouse_event(0x0010,0,0,0,[UIntPtr]::Zero) }
        else { [CCInput]::mouse_event(0x0002,0,0,0,[UIntPtr]::Zero); [CCInput]::mouse_event(0x0004,0,0,0,[UIntPtr]::Zero) }
      }
      'scroll' { [CCInput]::mouse_event(0x0800,0,0,[uint32]([int]$m.dy * -40),[UIntPtr]::Zero) }
      'key' {
        $k = "$($m.key)".ToLower()
        if ($VK.ContainsKey($k)) {
          [CCInput]::keybd_event([byte]$VK[$k],0,0,[UIntPtr]::Zero)
          [CCInput]::keybd_event([byte]$VK[$k],0,2,[UIntPtr]::Zero)
        } elseif ($m.key.Length -ge 1) {
          $t = $m.key -replace '([+^%~(){}\[\]])','{$1}'
          $sh.SendKeys($t)
        }
      }
      'text' { $t = $m.value -replace '([+^%~(){}\[\]])','{$1}'; $sh.SendKeys($t) }
    }
  } catch { }
}

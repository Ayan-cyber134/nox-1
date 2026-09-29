package com.roninble.test;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    static final UUID SERVICE = UUID.fromString("0000AE00-0000-1000-8000-00805F9B34FB");
    static final UUID WRITE = UUID.fromString("0000AE01-0000-1000-8000-00805F9B34FB");
    static final UUID NOTIFY = UUID.fromString("0000AE02-0000-1000-8000-00805F9B34FB");
    static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
    static final int REQ = 7;

    BluetoothAdapter adapter; BluetoothGatt gatt; BluetoothGattCharacteristic writeChar, notifyChar;
    BluetoothLeScanner scanner; boolean scanning; int sn=0;
    TextView log; EditText hexInput, volumeInput; LinearLayout devicesBox;
    Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle b) { super.onCreate(b); buildUi(); }

    void buildUi() {
        ScrollView sv = new ScrollView(this); LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(20,20,20,20); sv.addView(root);
        TextView title=new TextView(this); title.setText("RONiN BLE Test\nAE00 service / AE01 write / AE02 notify"); title.setTextSize(20); root.addView(title);
        Button scan=new Button(this); scan.setText("Scan"); root.addView(scan); devicesBox=new LinearLayout(this); devicesBox.setOrientation(LinearLayout.VERTICAL); root.addView(devicesBox);
        Button disconnect=new Button(this); disconnect.setText("Disconnect"); root.addView(disconnect);
        TextView v=new TextView(this); v.setText("Volume (0-255)"); root.addView(v); volumeInput=new EditText(this); volumeInput.setInputType(2); volumeInput.setText("50"); root.addView(volumeInput);
        Button setVol=new Button(this); setVol.setText("Send Set Volume (RCSP opcode 08)"); root.addView(setVol);
        TextView h=new TextView(this); h.setText("Raw HEX (spaces allowed)"); root.addView(h); hexInput=new EditText(this); hexInput.setHint("FE DC BA C0 08 ... EF"); root.addView(hexInput);
        Button send=new Button(this); send.setText("Send HEX"); root.addView(send);
        Button clear=new Button(this); clear.setText("Clear log"); root.addView(clear);
        log=new TextView(this); log.setTextIsSelectable(true); log.setTextSize(12); root.addView(log,new LinearLayout.LayoutParams(-1,500));
        setContentView(sv);
        scan.setOnClickListener(v1 -> startScan()); disconnect.setOnClickListener(v1 -> closeGatt()); clear.setOnClickListener(v1 -> log.setText(""));
        setVol.setOnClickListener(v1 -> { try { int x=Integer.parseInt(volumeInput.getText().toString()); if(x<0||x>255) throw new Exception(); send(buildVolume(x)); } catch(Exception e){ toast("Volume must be 0..255"); }});
        send.setOnClickListener(v1 -> { try { send(hex(hexInput.getText().toString())); } catch(Exception e){ toast("Invalid HEX"); }});
        ensurePermissions();
    }

    void ensurePermissions(){ ArrayList<String> p=new ArrayList<>(); if(Build.VERSION.SDK_INT>=31){ if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_SCAN); if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_CONNECT); } else if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.ACCESS_FINE_LOCATION); if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),REQ); }

    void startScan(){ ensurePermissions(); BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE); adapter=bm.getAdapter(); if(adapter==null||!adapter.isEnabled()){toast("Enable Bluetooth first");return;} scanner=adapter.getBluetoothLeScanner(); devicesBox.removeAllViews(); scanning=true; append("SCAN start"); scanner.startScan(scanCb); handler.postDelayed(()->{if(scanning){scanning=false;scanner.stopScan(scanCb);append("SCAN stop");}},10000); }
    final ScanCallback scanCb=new ScanCallback(){ @Override public void onScanResult(int type,ScanResult r){ BluetoothDevice d=r.getDevice(); String n=d.getName(); if(n==null)n="(no name)"; String s=n+"\n"+d.getAddress()+" RSSI="+r.getRssi(); for(int i=0;i<devicesBox.getChildCount();i++) if(String.valueOf(devicesBox.getChildAt(i).getTag()).equals(d.getAddress()))return; Button b=new Button(MainActivity.this);b.setText(s);b.setTag(d.getAddress());b.setOnClickListener(v->connect(d));devicesBox.addView(b); append("FOUND "+d.getAddress()+" "+n); }};

    void connect(BluetoothDevice d){ try{ if(scanning){scanning=false;scanner.stopScan(scanCb);} closeGatt(); append("CONNECT "+d.getAddress()); gatt=d.connectGatt(this,false,gattCb); }catch(SecurityException e){toast("Bluetooth permission denied");} }
    final BluetoothGattCallback gattCb=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){ append("CONN status="+status+" state="+state); if(state==BluetoothProfile.STATE_CONNECTED){ if(Build.VERSION.SDK_INT>=21)g.requestMtu(247); else g.discoverServices(); } else if(state==BluetoothProfile.STATE_DISCONNECTED){writeChar=null;notifyChar=null;} }
        @Override public void onMtuChanged(BluetoothGatt g,int mtu,int status){append("MTU "+mtu+" status="+status);g.discoverServices();}
        @Override public void onServicesDiscovered(BluetoothGatt g,int status){append("SERVICES status="+status); BluetoothGattService s=g.getService(SERVICE); if(s==null){append("AE00 NOT FOUND");dumpServices(g);return;} writeChar=s.getCharacteristic(WRITE);notifyChar=s.getCharacteristic(NOTIFY);append("AE01 write="+(writeChar!=null)+" AE02 notify="+(notifyChar!=null)); if(notifyChar!=null) enableNotify(g,notifyChar); }
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){append("CCCD write status="+status+" value="+hex(d.getValue()));}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){byte[] x=c.getValue();append("RX "+hex(x));}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){append("TX status="+status+" bytes="+hex(c.getValue()));}
    };
    void dumpServices(BluetoothGatt g){for(BluetoothGattService s:g.getServices()){append("SERVICE "+s.getUuid());for(BluetoothGattCharacteristic c:s.getCharacteristics())append("  CHAR "+c.getUuid()+" props="+c.getProperties());}}
    void enableNotify(BluetoothGatt g,BluetoothGattCharacteristic c){try{boolean ok=g.setCharacteristicNotification(c,true);append("setCharacteristicNotification="+ok);BluetoothGattDescriptor d=c.getDescriptor(CCCD);if(d==null){append("CCCD NOT FOUND");return;}d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);boolean w=g.writeDescriptor(d);append("write CCCD="+w);}catch(Exception e){append("NOTIFY error "+e);}}

    void send(byte[] data){if(gatt==null||writeChar==null){toast("Not connected to AE00 device");return;} try{writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);writeChar.setValue(data);boolean ok=gatt.writeCharacteristic(writeChar);append("WRITE requested="+ok+" "+hex(data));}catch(Exception e){append("WRITE error "+e);}}
    byte[] buildVolume(int vol){int seq=sn++ & 255; return new byte[]{(byte)0xFE,(byte)0xDC,(byte)0xBA,(byte)0xC0,0x08,0x00,0x04,(byte)seq,0x0F,0x02,0x01,(byte)vol,(byte)0xEF};}
    static byte[] hex(String s){s=s.replaceAll("[^0-9A-Fa-f]","");if((s.length()&1)!=0)throw new IllegalArgumentException();byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    static String hex(byte[] b){if(b==null)return "null";StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02X ",x&255));return s.toString().trim();}
    void closeGatt(){try{if(gatt!=null){gatt.disconnect();gatt.close();}}catch(Exception ignored){}gatt=null;writeChar=null;notifyChar=null;}
    void append(String s){runOnUiThread(()->{log.append(s+"\n");});}
    void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}
    @Override protected void onDestroy(){super.onDestroy();closeGatt();}
}

package com.carrera.bluetooth3d;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "TurboBT";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String APP_NAME = "TurboBluetoothRacing";

    private WebView webView;
    private BluetoothAdapter bluetoothAdapter;

    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = new WebView(this);
        setContentView(webView);

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBluetooth");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        requestBluetoothPermissions();
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean needScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED;
            boolean needConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED;
            boolean needAdvertise = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED;

            if (needScan || needConnect || needAdvertise) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_ADVERTISE
                        },
                        101
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            boolean granted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (!granted) {
                Toast.makeText(this, "Permisos de Bluetooth requeridos para el modo multijugador", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void notifyJsStatus(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    webView.evaluateJavascript("if(window.onBluetoothStatus) window.onBluetoothStatus('" + status + "');", null);
                }
            }
        });
    }

    private void notifyJsData(final String data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    String escaped = data.replace("\\", "\\\\").replace("'", "\\'");
                    webView.evaluateJavascript("if(window.onBluetoothData) window.onBluetoothData('" + escaped + "');", null);
                }
            }
        });
    }

    private synchronized void manageConnectedSocket(BluetoothSocket socket) {
        stopThreads();
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
        notifyJsStatus("CONNECTED");
    }

    private synchronized void stopThreads() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    public class WebAppInterface {

        @JavascriptInterface
        public void showToast(String message) {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public String getDeviceName() {
            return Build.MODEL;
        }

        @JavascriptInterface
        public void connectBluetooth() {
            try {
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                MainActivity.this.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "No se pudo abrir la configuración de Bluetooth", Toast.LENGTH_SHORT).show();
            }
        }

        @JavascriptInterface
        public void closeApp() {
            finishAffinity();
        }

        @JavascriptInterface
        public boolean isBluetoothReady() {
            return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
        }

        @JavascriptInterface
        public String getPairedDevices() {
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                return "[]";
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return "[]";
            }
            try {
                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                JSONArray array = new JSONArray();
                for (BluetoothDevice device : pairedDevices) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", device.getName() != null ? device.getName() : "Dispositivo Desconocido");
                    obj.put("address", device.getAddress());
                    array.put(obj);
                }
                return array.toString();
            } catch (Exception e) {
                Log.e(TAG, "Error obteniendo dispositivos emparejados", e);
                return "[]";
            }
        }

        @JavascriptInterface
        public void makeDiscoverable() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermissions();
                return;
            }
            try {
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                startActivity(discoverableIntent);
            } catch (Exception e) {
                Log.e(TAG, "Error haciendo el dispositivo visible", e);
            }
        }

        @JavascriptInterface
        public void startServer() {
            if (!isBluetoothReady()) {
                showToast("Bluetooth no disponible o desactivado");
                notifyJsStatus("FAILED");
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermissions();
                showToast("Permiso de dispositivos cercanos requerido");
                notifyJsStatus("FAILED");
                return;
            }
            stopThreads();
            acceptThread = new AcceptThread();
            acceptThread.start();
            notifyJsStatus("WAITING");
        }

        @JavascriptInterface
        public void connectToDevice(String address) {
            if (!isBluetoothReady()) {
                showToast("Bluetooth no disponible");
                notifyJsStatus("FAILED");
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermissions();
                showToast("Permiso de dispositivos cercanos requerido");
                notifyJsStatus("FAILED");
                return;
            }
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                stopThreads();
                connectThread = new ConnectThread(device);
                connectThread.start();
                notifyJsStatus("CONNECTING");
            } catch (Exception e) {
                Log.e(TAG, "Error al conectar con dispositivo " + address, e);
                showToast("Error al conectar con " + address);
                notifyJsStatus("FAILED");
            }
        }

        @JavascriptInterface
        public void sendData(String data) {
            if (connectedThread != null) {
                connectedThread.write((data + "\n").getBytes());
            }
        }

        @JavascriptInterface
        public void disconnect() {
            stopThreads();
            notifyJsStatus("DISCONNECTED");
        }
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "No se puede iniciar AcceptThread sin permiso BLUETOOTH_CONNECT");
                serverSocket = null;
                return;
            }
            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Socket listen() failed", e);
            }
            serverSocket = tmp;
        }

        @Override
        public void run() {
            BluetoothSocket socket = null;
            while (serverSocket != null) {
                try {
                    socket = serverSocket.accept();
                } catch (Exception e) {
                    Log.e(TAG, "Socket accept() failed", e);
                    break;
                }
                if (socket != null) {
                    manageConnectedSocket(socket);
                    try {
                        serverSocket.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Could not close ServerSocket", e);
                    }
                    break;
                }
            }
        }

        public void cancel() {
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "Could not close ServerSocket", e);
            }
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Socket create() failed", e);
            }
            mmSocket = tmp;
        }

        @Override
        public void run() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
            }

            if (mmSocket == null) {
                notifyJsStatus("FAILED");
                return;
            }

            try {
                mmSocket.connect();
            } catch (Exception e) {
                try {
                    mmSocket.close();
                } catch (Exception e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                notifyJsStatus("FAILED");
                return;
            }
            manageConnectedSocket(mmSocket);
        }

        public void cancel() {
            try {
                if (mmSocket != null) mmSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "Could not close client socket", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (Exception e) {
                Log.e(TAG, "Error al obtener streams", e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            StringBuilder sb = new StringBuilder();

            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    if (bytes > 0) {
                        String readMessage = new String(buffer, 0, bytes);
                        sb.append(readMessage);
                        int endOfLineIndex;
                        while ((endOfLineIndex = sb.indexOf("\n")) >= 0) {
                            String message = sb.substring(0, endOfLineIndex).trim();
                            sb.delete(0, endOfLineIndex + 1);
                            if (!message.isEmpty()) {
                                notifyJsData(message);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Input stream disconnected", e);
                    notifyJsStatus("DISCONNECTED");
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (Exception e) {
                Log.e(TAG, "Error writing to stream", e);
            }
        }

        public void cancel() {
            try {
                if (mmSocket != null) mmSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "Could not close socket", e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopThreads();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

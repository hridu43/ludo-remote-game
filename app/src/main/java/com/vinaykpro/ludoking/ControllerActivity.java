package com.vinaykpro.ludoking;

public class ControllerActivity extends android.app.Activity {
    private android.net.nsd.NsdManager nsd;
    private android.net.nsd.NsdManager.DiscoveryListener discovery;
    private android.net.wifi.WifiManager.MulticastLock multicastLock;
    private String host = null;
    private int port = 8765;
    private boolean resolving = false;
    private boolean discovering = false;
    private android.widget.TextView status;

    @Override
    protected void onCreate(android.os.Bundle state) {
        super.onCreate(state);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setPadding(24, 30, 24, 30);
        layout.setBackgroundColor(android.graphics.Color.rgb(23, 37, 84));

        status = new android.widget.TextView(this);
        status.setTextColor(android.graphics.Color.WHITE);
        status.setTextSize(18);
        status.setGravity(android.view.Gravity.CENTER);
        status.setText("Searching for Ludo game...");
        layout.addView(status, new android.widget.LinearLayout.LayoutParams(-1, 90));

        addButton(layout, "ROLL DICE", "roll", 24);
        for (int i = 0; i < 4; i++)
            addButton(layout, "MOVE PIECE " + (i + 1), "move&index=" + i, 18);

        android.widget.Button retry = new android.widget.Button(this);
        retry.setText("SEARCH AGAIN");
        retry.setOnClickListener(v -> {
            host = null;
            resolving = false;
            startDiscovery();
        });
        layout.addView(retry);

        setContentView(layout);

        android.net.wifi.WifiManager wifi =
                (android.net.wifi.WifiManager) getApplicationContext()
                        .getSystemService(WIFI_SERVICE);
        multicastLock = wifi.createMulticastLock("LudoRemoteDiscovery");
        multicastLock.setReferenceCounted(false);
        multicastLock.acquire();

        nsd = (android.net.nsd.NsdManager) getSystemService(NSD_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission("android.permission.NEARBY_WIFI_DEVICES")
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{"android.permission.NEARBY_WIFI_DEVICES"}, 2001);
        } else {
            startDiscovery();
        }
    }

    private void addButton(android.widget.LinearLayout layout,
                           String label, String command, int size) {
        android.widget.Button b = new android.widget.Button(this);
        b.setText(label);
        b.setTextSize(size);
        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 7, 0, 7);
        layout.addView(b, lp);
        b.setOnClickListener(v -> sendCommand(command));
    }

    private void startDiscovery() {
        if (discovering) {
            try { nsd.stopServiceDiscovery(discovery); } catch (Exception ignored) {}
            discovering = false;
        }
        host = null;
        resolving = false;
        status.setText("Searching for game phone...");

        discovery = new android.net.nsd.NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String type) { discovering = true; }
            public void onDiscoveryStopped(String type) { discovering = false; }
            public void onStartDiscoveryFailed(String type, int error) {
                discovering = false;
                status.setText("Search failed. Tap SEARCH AGAIN.");
            }
            public void onStopDiscoveryFailed(String type, int error) {
                discovering = false;
            }
            public void onServiceLost(android.net.nsd.NsdServiceInfo service) {}
            public void onServiceFound(android.net.nsd.NsdServiceInfo service) {
                if (!resolving && service.getServiceName().startsWith("LudoRemote-")) {
                    resolving = true;
                    nsd.resolveService(service,
                        new android.net.nsd.NsdManager.ResolveListener() {
                            public void onResolveFailed(
                                    android.net.nsd.NsdServiceInfo info, int error) {
                                resolving = false;
                                status.setText("Could not connect. Searching again...");
                            }
                            public void onServiceResolved(
                                    android.net.nsd.NsdServiceInfo info) {
                                host = info.getHost().getHostAddress();
                                port = info.getPort();
                                status.setText("CONNECTED to " + info.getServiceName());
                                try { nsd.stopServiceDiscovery(discovery); }
                                catch (Exception ignored) {}
                                discovering = false;
                            }
                        });
                }
            }
        };

        try {
            nsd.discoverServices("_ludoremote._tcp.",
                    android.net.nsd.NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (Exception e) {
            status.setText("Discovery unavailable. Tap SEARCH AGAIN.");
        }
    }

    private void sendCommand(String command) {
        final String address = host;
        if (address == null) {
            status.setText("Game phone not found yet. Keep both phones on Wi-Fi.");
            return;
        }
        status.setText("Sending command...");
        new Thread(() -> {
            java.net.HttpURLConnection connection = null;
            try {
                java.net.URL url = new java.net.URL(
                        "http://" + address + ":" + port + "/cmd?cmd=" + command);
                connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                int code = connection.getResponseCode();
                runOnUiThread(() -> status.setText(
                        code == 200 ? "Command sent" : "Game phone error"));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Connection lost. Tap SEARCH AGAIN.");
                    host = null;
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 2001 && results.length > 0 &&
                results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
            startDiscovery();
        else
            status.setText("Nearby devices permission is required.");
    }

    @Override
    protected void onDestroy() {
        try {
            if (discovering) nsd.stopServiceDiscovery(discovery);
        } catch (Exception ignored) {}
        try {
            if (multicastLock != null && multicastLock.isHeld())
                multicastLock.release();
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}

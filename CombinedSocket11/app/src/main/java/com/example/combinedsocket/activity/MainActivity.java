package com.example.combinedsocket.activity;

// MainActivity.java


import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.example.combinedsocket.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MainActivity extends AppCompatActivity {

    private ServerSocket serverSocket;
    private Thread serverThread = null;
    private Thread clientThread = null;
    private TextView tvIP, tvPort, tvMessages;
    private EditText etMessage;
    private Button btnSend, btnToggleMode;
    private PrintWriter output;
    private BufferedReader input;
    private Socket clientSocket;
    private static final long RECONNECT_DELAY = 5000;

    private static final int SERVER_PORT = 8080;
    private String serverIP = "";
    private boolean isServer = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvIP = findViewById(R.id.tvIP);
        tvPort = findViewById(R.id.tvPort);
        tvMessages = findViewById(R.id.tvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnToggleMode = findViewById(R.id.btnToggleMode);

        try {
            serverIP = getLocalIpAddress(getApplicationContext());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = etMessage.getText().toString().trim();
                if (!message.isEmpty()) {
                    if (isServer) {
                        new Thread(new ServerSendThread(message)).start();
                    } else {
                        new Thread(new ClientSendThread(message)).start();
                    }
                }
            }
        });

        btnToggleMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMode();
            }
        });

        toggleMode(); // Initially, set the app to server mode
    }

    private void toggleMode() {
        isServer = !isServer;
        if (isServer) {
            startServer();
        } else {
            serverIP ="192.168.1.78";
            startClient();
        }
    }

    private void startServer() {
        if (clientSocket != null && !clientSocket.isClosed()) {
            closeClientSocket();
        }

        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
        }

        serverThread = new Thread(new ServerThread());
        serverThread.start();

        runOnUiThread(() -> {
            tvMessages.setText("Waiting for connections...");
            tvIP.setText("IP: " + serverIP);
            tvPort.setText("Port: " + SERVER_PORT);
        });
    }

    private void startClient() {
        // Close existing client socket if needed
        closeClientSocket();

        if (clientThread != null && clientThread.isAlive()) {
            clientThread.interrupt();
        }

        // Create a new Socket for the client
        clientThread = new Thread(new ClientThread());
        clientThread.start();
    }

    private String getLocalIpAddress(Context context) throws UnknownHostException {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();
            String ipString = Formatter.formatIpAddress(ipAddress);
            return ipString;
        }
        return null;
    }

    class ServerThread implements Runnable {
        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(SERVER_PORT);

                runOnUiThread(() -> {
                    tvMessages.setText("Waiting for connections...");
                    tvIP.setText("IP: " + serverIP);
                    tvPort.setText("Port: " + SERVER_PORT);
                });

                while (true) {
                    clientSocket = serverSocket.accept();
                    output = new PrintWriter(clientSocket.getOutputStream());
                    input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                    runOnUiThread(() -> tvMessages.setText("Connected"));

                    // Start a new thread for the current client
                    new Thread(new ServerReceiveThread()).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> tvMessages.append("Error in ServerThread: " + e.getMessage()));
            }
        }
    }


    class ServerReceiveThread implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    String message = input.readLine();
                    if (message != null) {
                        runOnUiThread(() -> tvMessages.append("Client: " + message + "\n"));
                    } else {
                        runOnUiThread(() -> tvMessages.append("Client disconnected\n"));
                        closeClientSocket(); // Clean up resources associated with the client
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> tvMessages.append("Error in ServerReceiveThread: " + e.getMessage()));
            }
        }
    }

    class ServerSendThread implements Runnable {
        private String message;

        ServerSendThread(String message) {
            this.message = message;
        }

        @Override
        public void run() {
            output.write(message + "\n");
            output.flush();
            runOnUiThread(() -> {
                tvMessages.append("Server: " + message + "\n");
                etMessage.setText("");
            });
        }
    }

    class ClientThread implements Runnable {
        private static final long RECONNECT_DELAY = 5000; // 5 seconds delay for reconnection

        @Override
        public void run() {
            while (true) {
                try {
                    clientSocket = new Socket(serverIP, SERVER_PORT);
                    output = new PrintWriter(clientSocket.getOutputStream());
                    input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                    runOnUiThread(() -> tvMessages.setText("Connected"));

                    new Thread(new ClientReceiveThread()).start();

                    // If the client has successfully connected, break out of the loop
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> tvMessages.append("Error in ClientThread: " + e.getMessage() + "\n"));

                    // If there is an error, sleep for a while before attempting to reconnect
                    try {
                        Thread.sleep(RECONNECT_DELAY);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }


    class ClientReceiveThread implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    String message = input.readLine();
                    if (message != null) {
                        runOnUiThread(() -> tvMessages.append("Server: " + message + "\n"));
                    } else {
                        runOnUiThread(() -> tvMessages.append("Server disconnected\n"));

                        // Connection lost, attempt to reconnect
                        reconnectToServer();
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> tvMessages.append("Error in ClientReceiveThread: " + e.getMessage() + "\n"));

                // Connection lost, attempt to reconnect
                reconnectToServer();
            }
        }

        private void reconnectToServer() {
            // Close the existing client socket
            closeClientSocket();

            // Attempt to reconnect in a loop
            while (true) {
                try {
                    clientSocket = new Socket(serverIP, SERVER_PORT);
                    output = new PrintWriter(clientSocket.getOutputStream());
                    input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                    runOnUiThread(() -> tvMessages.setText("Connected"));

                    // Start a new thread for the current client
                    new Thread(new ClientReceiveThread()).start();

                    // If reconnection is successful, break out of the loop
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> tvMessages.append("Error in reconnectToServer: " + e.getMessage() + "\n"));

                    // If there is an error, sleep for a while before attempting to reconnect
                    try {
                        Thread.sleep(RECONNECT_DELAY);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    class ClientSendThread implements Runnable {
        private String message;

        ClientSendThread(String message) {
            this.message = message;
        }

        @Override
        public void run() {
            output.write(message + "\n");
            output.flush();
            runOnUiThread(() -> {
                tvMessages.append("Client: " + message + "\n");
                etMessage.setText("");
            });
        }
    }

    private void closeClientSocket() {
        runOnUiThread(() -> {
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                    tvMessages.append("Client socket closed\n");
                }
                // Do not close the server socket here
            } catch (IOException e) {
                e.printStackTrace();
                tvMessages.append("Error in closeClientSocket: " + e.getMessage() + "\n");
            }
        });
    }
}

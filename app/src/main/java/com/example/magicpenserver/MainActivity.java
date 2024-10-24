package com.example.magicpenserver;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MainActivity";

    private static final int PORT = 12345;
    private ServerSocket serverSocket;
    private final List<Socket> clientSockets = new ArrayList<>();
    private static final int PICK_IMAGE_REQUEST = 1;

    private ImageView imageView;
    Button button1;
    Button button2;
    Button button3;
    Button selectBtn;
    Socket socket;
    Uri imageUri;
    String imagePath;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initView();

        //启动服务器，等待连接...
        new Thread(this::startServer).start();
    }

    private void initView() {
        imageView = findViewById(R.id.image_view);
        button1 = findViewById(R.id.deviceA_btn);
        button2 = findViewById(R.id.deviceB_btn);
        button3 = findViewById(R.id.deviceC_btn);
        selectBtn = findViewById(R.id.select_btn);
        selectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectImage();
            }
        });
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleButton1Click();
            }
        });
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleButton2Click();
            }
        });
        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleButton3Click();
            }
        });
    }

    private void startServer() {
        try  {
            serverSocket = new ServerSocket(PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept(); // 等待客户端连接
                synchronized (clientSockets) {
                    clientSockets.add(clientSocket); // 添加新客户端
                }
                Log.i(TAG,"startServer clientSockets: "+clientSockets);
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "客户端已连接", Toast.LENGTH_SHORT).show()
                );
                // 在这里可以处理客户端的连接
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void handleButton1Click() {
        sendImageToClient(imagePath,0);
    }
    private void handleButton2Click() {
        sendImageToClient(imagePath,1);
    }
    private void handleButton3Click() {
        sendImageToClient(imagePath,2);
    }

    private void selectImage() {
        // 使用 Intent 打开相册选择图片
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }
    private void sendImageToClient(String imagePath, int clientIndex) {
        Log.i(TAG,"sendImageToClient clientIndex: "+clientIndex + ", clientSockets: "+clientSockets);
        if (clientIndex < clientSockets.size()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Socket clientSocket = clientSockets.get(clientIndex);
                    Log.i(TAG,"sendImageToClient clientSocket: "+clientSocket);

                    // 发送图片
                    assert imagePath != null;
                    File file = new File(imagePath); // 图片路径
                    FileInputStream fileInputStream = null;
                    try {
                        fileInputStream = new FileInputStream(file);
                        if (clientSocket != null){
                            OutputStream outputStream = clientSocket.getOutputStream();
                            byte[] buffer = new byte[4096];
                            int bytesRead;

                            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }

                            outputStream.flush();
                            fileInputStream.close();
                            System.out.println("图片发送完毕。");
                            runOnUiThread(() ->
                                    Toast.makeText(MainActivity.this, "图片发送完毕。", Toast.LENGTH_LONG).show()
                            );
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
//                    finally {
//                        // 确保在出错时关闭连接
//                        if (clientSocket != null && !clientSocket.isClosed()) {
//                            try {
//                                clientSocket.close();
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    }
                }
            }).start();
        }


    }

    private void displayImage(Uri uri) {
        // 通过 URI 设置 ImageView 的图片
        imageView.setImageURI(uri);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            imageUri = data.getData();
            imagePath = getPathFromUri(imageUri);
            Log.i(TAG,"imageUri: "+imageUri);
            Log.i(TAG,"imagePath: "+imagePath);
            if (imageUri != null) {
                displayImage(imageUri);
            } else {
                Toast.makeText(this, "未选择任何图片", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getPathFromUri(Uri uri) {
        String path = null;
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);

        if (cursor != null) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            path = cursor.getString(columnIndex);
            cursor.close();
        }

        return path;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 关闭所有客户端连接
        for (Socket socket : clientSockets) {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        clientSockets.clear(); // 清空客户端列表

        // 关闭服务器Socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class backup1 extends Activity {
    ConcurrentHashMap<String, LinkedList<Double>> msgMap = new ConcurrentHashMap<String, LinkedList<Double>>();
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    //static final String REMOTE_PORT[] = {"11108","11112","11116","11120","11124"};
    static final LinkedList<String> REMOTE_PORTS = new LinkedList<String>();
    static final String PROPOSED = "PROPOSED";
    static final String UNDELIVERABLE = "UNDELIVERABLE";
    static final String DELIVERABLE = "DELIVERABLE";
    static final int SERVER_PORT = 10000;
    static HashMap<String, Timer> timerMap = new HashMap<String, Timer>();

    static HashMap<String, LinkedList<String>> portMsgs = new HashMap<String, LinkedList<String>>();
    static HashMap<String, String> messageTye = new HashMap<String, String>();
    static HashMap<String, Boolean> status = new HashMap<String, Boolean>();
    static int sequence=0;
    int contentSeqNo=0;
    // all processes and the sequence nubers of theire msgs
    static HashMap<String, Integer> procSeqNo = new HashMap<String, Integer>();

    TextView tv;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length()-4);
        final String myPort = String.valueOf(Integer.parseInt(portStr)*2);

        REMOTE_PORTS.add("11108");
        REMOTE_PORTS.add("11112");
        REMOTE_PORTS.add("11116");
        REMOTE_PORTS.add("11120");
        REMOTE_PORTS.add("11124");

        status.put("11108",false);
        status.put("11112",false);
        status.put("11116",false);
        status.put("11120",false);
        status.put("11124",false);

        portMsgs.put("11108",new LinkedList<String>());
        portMsgs.put("11112",new LinkedList<String>());
        portMsgs.put("11116",new LinkedList<String>());
        portMsgs.put("11120",new LinkedList<String>());
        portMsgs.put("11124",new LinkedList<String>());

        procSeqNo.put(REMOTE_PORTS.get(0),0);
        procSeqNo.put(REMOTE_PORTS.get(1),0);
        procSeqNo.put(REMOTE_PORTS.get(2),0);
        procSeqNo.put(REMOTE_PORTS.get(3),0);
        procSeqNo.put(REMOTE_PORTS.get(4),0);



        try{
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        }
        catch(Exception e){
            Log.e(TAG,"Cant create a socket!");
        }
        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        Button btn = (Button)findViewById(R.id.button4);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText= (EditText) findViewById(R.id.editText1);
                String msg = editText.getText().toString();
                editText.setText("");
                // tv.append(msg);
                // tv.append("\n");

                String msgToSend = "PROPOSED" + "#" + msg + "#" + ++sequence + "#" + myPort;
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msgToSend, myPort);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void>{

        // int seqNo=0;
        private Uri buildUri(String scheme, String authority) {
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority(authority);
            uriBuilder.scheme(scheme);
            return uriBuilder.build();
        }
        @Override
        protected Void doInBackground(ServerSocket ... sockets) {

            ServerSocket serverSocket = sockets[0];
            Socket socket=null;
            String msg,msg1;
            String msgToSend=null;
            String arr[];
            String msgType;
            String senderPort=null;
            String receiverPort;
            int seq;
            LinkedList<Double> portSeqNo=null;
            String key;
            double value=0.0;
            int portMsgCount =0;
            int deviceCount;

            Uri uri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");



            do {
                try {
                    socket = serverSocket.accept();
                    //socket.setSoTimeout(3000);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    msg = in.readUTF();
                    //   Log.v(TAG, " Receving Msg from Client msg!"+msg);

                    arr = msg.split("#");
                    msgType = arr[0];
                    msg = arr[1];
                    seq = Integer.parseInt(arr[2]);
                    senderPort = arr[3];
                    receiverPort = arr[4];
                    portMsgCount = Integer.parseInt(arr[5]);
                    deviceCount = Integer.parseInt(arr[6]);

                    //   Log.v(TAG,"Client sent Msg: "+msg +" to receiver "+receiverPort);
                    key = senderPort + "_" + msg + "_" + portMsgCount;
                    value = Double.parseDouble(senderPort+"."+ portMsgCount+deviceCount);
                    if(msgType.equals(PROPOSED)){
                        msgType = UNDELIVERABLE;
                        msgToSend = msgType + "#" + msg + "#" + key + "#" + value + "#" + receiverPort;
                        DataOutputStream os = new DataOutputStream(socket.getOutputStream());
                        os.writeUTF(msgToSend);

//                        Timer time = new Timer();
//
//                        time.schedule(new failureHandler(senderPort), 3000);
//                        timerMap.put(senderPort,time);

                        //   Log.v(TAG, " UnDeliverale!");
                    }


                    if(msgType.equals(DELIVERABLE)){

                        ContentValues cv = new ContentValues();

                        cv.put("key",contentSeqNo);
                        cv.put("value",msg);
                        getContentResolver().insert(uri,cv);

                        // String s=  contentSeqNo.toString();
                        // cv.containsKey(Integer.toString(contentSeqNo));
                        // Log.v(TAG,"Checking contnt values: "+cv.get(Integer.toString(contentSeqNo)));

                        Log.v(TAG, " Content key!" + contentSeqNo + "value" + arr[7]);
                        msgToSend = contentSeqNo + ") " + msg + " " + arr[7];
                        publishProgress(msgToSend);
                        ++contentSeqNo;
                    }
                    //  Log.v(TAG,"if: list size:"+portSeqNo.size());
                    //arr[2] contains sequence no
                }


                catch (Exception e) {
                    Log.e(TAG, " Cant accept connection");
                }

            }while(true);
        }

        protected void onProgressUpdate(String...strings){
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            // TextView localTextView = (TextView) findViewById(R.id.local_text_display);
            // localTextView.append("\n");

            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */

            String filename = "GroupMessengerOutput";
            String string = strReceived + "\n";
            FileOutputStream outputStream;

            try {
                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(string.getBytes());
                outputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "File write failed");
            }
        }

    }


    private class failureHandler extends TimerTask
    {
        String msgId;
        public failureHandler(String id)
        {
            msgId = id;
        }

        @Override
        public void run() {
            // Message temp = holdBackMap.get(msgId);
            // prioritySet.remove(temp);
            // tv.append("Process Failed "+msgId);
            Log.v(TAG,"Process failed: "+ msgId);
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void>{
        @Override
        protected Void doInBackground(String...msgs){

            int deviceCount=0;
            LinkedList<Double> portSeqNo=null;
            String msgType;
            String msg=null;
            String arr[];
            String key;
            double value;
            String msgToSend;
            String senderPort=null;
            String remotePort=null;
            int i;

            Log.v(TAG,"Check Remote port list: " + REMOTE_PORTS);
            try {

                for (i = 0; i < REMOTE_PORTS.size(); i++) {

                    remotePort = REMOTE_PORTS.get(i);
                    Log.v(TAG,"Check Port: " + REMOTE_PORTS.get(i));
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(remotePort));
                    socket.setSoTimeout(500);

                    status.put(remotePort,true);  //
                    msgToSend = msgs[0];
                    senderPort = msgs[1];
                    // Log.v(TAG,"Sender port "+ senderPort);



                    DataOutputStream os = new DataOutputStream(socket.getOutputStream());
                    ++deviceCount;
                    msgToSend = msgToSend+"#"+ remotePort + "#" + sequence + "#" + deviceCount;

                    //hashmap msgId, type
                    //hashmap sender port, sent msg

                    os.writeUTF(msgToSend);

                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    msg = in.readUTF();
                    arr = msg.split("#");

                    msgType = arr[0];
                    msg = arr[1];
                    key = arr[2];
                    value = Double.parseDouble(arr[3]);
                    String receiverPort = arr[4];

                    if(msgType.equals(UNDELIVERABLE)){
                        if(!msgMap.containsKey(key)){
                            //Log.v(TAG,"check key:"+key);
                            portSeqNo = new LinkedList<Double>();
                            portSeqNo.add(value);
                            msgMap.put(key , portSeqNo);

//                            Timer time = new Timer();
//                            time.schedule(new failureHandler(remotePort), 3000);
//                            timerMap.put(senderPort,time);
                        } else{
                            portSeqNo= msgMap.get(key);
                            portSeqNo.add(value);
                            msgMap.put(key , portSeqNo);
                        }
                        LinkedList<Double> l = msgMap.get(key);
                        // Log.v(TAG,"Check key: "+ k + "Check Value: " + l);
                        if(l.size()==5) {


                            double max = l.get(0);
                            for (i = 1; i < 5; i++) {
                                if (max < l.get(i)) {
                                    max = l.get(i);
                                }
                            }
                            synchronized (this){
                                for (i = 0; i < REMOTE_PORTS.size(); i++) {
                                    remotePort = REMOTE_PORTS.get(i);
                                    Socket socket2 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(remotePort));
                                    msgType = DELIVERABLE;
                                    Log.v(TAG, "Check which thread: " + this + "Msg no: " + max);
                                    msgToSend = msgType + "#" + msg + "#" + sequence + "#" + senderPort + "#" + remotePort + "#" + sequence + "#" + deviceCount + "#" + max;
                                    DataOutputStream os1 = new DataOutputStream(socket2.getOutputStream());
                                    os1.writeUTF(msgToSend);
                                    // Log.v(TAG,"MsgToSend "+ i + ") " + msgToSend);
                                    //remove from hashmap
                                }
                                msgMap.remove(key);
                                //socket2.close();
                            }
                        }



                    }
                }
            }
//            catch(InterruptedIOException e){
//                Log.e(TAG,"Cannot connect to remote port");
//            }
            catch (SocketTimeoutException e){
                Log.e(TAG,"Cannot connect to remote port" + e + remotePort );
            }
            catch(Exception e){
                Log.e(TAG,e + "Exception: "+ remotePort + " failed");
                status.put(remotePort,false);
            }
            return null;
        }
    }
}





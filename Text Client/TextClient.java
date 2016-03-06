/**
 * COEN 317
 * Distributed Editor
 * 
 * Client
 */

import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.text.*;
import java.util.*;

public class TextClient {
    private static ObjectInputStream sInput;
    private static ObjectOutputStream sOutput;
    
    private Socket socket;
    private String server, username;
    private int port;
    
    private static SimpleDateFormat sdf;
    private static TextEditor editor;

    TextClient(String server, int port, String username, TextEditor editor) {
        this.server = server;
        this.port = port;
        this.username = username;
        this.editor = editor;
        
        sdf = new SimpleDateFormat("YYYY.MM.dd, hh:mm:ss a");
    }
    
    //Make a connection with the server
    public boolean start() {
        //Connect to the server
        try {
            socket = new Socket(server, port);
        } catch(Exception e) {
            display("Error connectiong to server");
            return false;
        }
        
        display("Connection accepted " + socket.getInetAddress() + ":" + socket.getPort());

        //Create both Data Stream
        try {
            sInput  = new ObjectInputStream(socket.getInputStream());
            sOutput = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            display("Exception creating new Input/output Streams: " + e);
            return false;
        }

        //Start the Server Listener
        new ServerListener().start();
        
        //Send username to the server as a String
        try {
            sOutput.writeObject(username);
        } catch (IOException e) {
            display("Exception doing login : " + e);
            disconnect();
            return false;
        }
        
        return true;
    }

    //Display message to the console with the time stamp
    private static void display(String msg) {
        String message = sdf.format(new Date()) + "\n" + msg;
        
        if(editor == null) {
            System.out.println(message);
        } else {
            editor.display(message + "\n");
        }
    }
	
    //Send a message to the server
    public void sendMessage(ChatMessage msg) {
        try {
            sOutput.writeObject(msg);
        } catch (IOException ex) {
            /* ¯\_(ツ)_/¯ */
        }
    }
    
    //Disconnect client from server
    private void disconnect() {
        try { 
            if(sInput != null) sInput.close();
        } catch(Exception e) {}

        try {
            if(sOutput != null) sOutput.close();
        } catch(Exception e) {}

        try{
            if(socket != null) socket.close();
        } catch(Exception e) {}
        
        if(editor != null) {
            editor.connectionFailed();
        }
    }
    
    //Send a file to the server
    public static void sendFile(String fileName) {
        try {
            File f = new File(fileName);
            
            byte[] content = Files.readAllBytes(f.toPath());
            sOutput.writeObject(content);
            
        } catch (FileNotFoundException e) {
            display("File Not Found: " + fileName + " Not Found");

        } catch (IOException e) {
            display("Error sending file " + fileName);
        }
    }
    
    //Receive a file from the server
    public static void receiveFile(String fileName) {
        try {
            File f = new File(fileName);

            byte[] content = (byte[]) sInput.readObject();
            Files.write(f.toPath(), content);

            display("File Received: " + fileName);
            editor.displayFile(fileName);

        } catch (IOException | ClassNotFoundException ex) {
            display("Error receiving file " + fileName);
        }
    }
    
    //Class that waits for message from the server
    class ServerListener extends Thread {
        @Override
        public void run() {
            while(true) {
                try {
                    String msg = (String) sInput.readObject();
                    display(msg);
                    
                    //If the server sends a "Receiving /directory/filename" message
                    if (msg.toLowerCase().startsWith("Receiving ".toLowerCase())) {
                        //Means that a file is coming
                        //To get the file name, it is the substring after the last "/" in "/directory/filename"
                        int index = Math.max(msg.lastIndexOf("/"), msg.lastIndexOf("\\"));
                        String fileName = msg.substring(index + 1);
                        receiveFile(fileName);
                    } else if (msg.toLowerCase().startsWith("Username is now: ".toLowerCase())) {
                        username = msg.substring(17);
                        editor.username = username;
                        editor.label.setText("Hello " + username + ". Enter you commands below");
                    }
                    
                    if(editor == null) {
                        display("> ");
                    }
                    
                } catch(IOException e) {
                    if(editor != null) {
                        editor.connectionFailed();
                    }
                    break;
                } catch(ClassNotFoundException e) {}
            }
        }
    }
}
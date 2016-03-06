/*
 * COEN 317
 * Distributed Editor
 * Server
 *
 */

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.text.SimpleDateFormat;

public class TextServer {
    private static int uniqueID;
    
    private static ServerSocket serverSocket;
    private static int port;

    //Keep track of clients
    private static ArrayList<ClientThread> list;
    private SimpleDateFormat sdf;

    public TextServer(int port) {
        this.port = port;
        
        //Date Format: Year.Month.Day, time in 24 hour clock format
        sdf = new SimpleDateFormat("YYYY.MM/dd, HH:mm:ss a");
        list = new ArrayList<>();
    }
    
    //Print out message to console with time stamp
    private void event(String msg) {
        String message = sdf.format(new Date())+ ": " + msg;
        System.out.println(message);
    }
    
    //Broadcast a message to all Clients
    private synchronized void broadcast(String message, String username) {
        event(username + " has sent the message: \"" + message + "\"");

        //Loop in reverse order in case we would have to remove a Client
        for(int i = list.size() - 1; i >= 0; i--) {
            ClientThread ct = list.get(i);
            System.out.println("Sending a message to client " + i);
            
            //Try to write to the Client; if it fails remove Client from the list
            if(!ct.writeMsg(username + ": " + message)) {
                list.remove(i);
                event("Disconnected Client " + i + ": " + ct.username + " removed from list");
            }
        }
    }
    
    //Remove Client who logs out (using the LOGOUT message)
    private synchronized void remove(int id) {
        for(int i = 0; i < list.size(); ++i) {
            ClientThread ct = list.get(i);
            
            if(ct.id == id) {
                list.remove(i);
                event("Disconnected Client " + i + ": " + ct.username + " removed from list");
                return;
            }
        }
    }
    
    //Start and run the server
    public void start() {
        try {
            //Start listening on port
            serverSocket = new ServerSocket(port);
            
            event("Web Server running on Inet Address " + serverSocket.getInetAddress()
                    + " port: " + serverSocket.getLocalPort());
            
            System.out.println("Working Directory: \"" +
                        System.getProperty("user.dir").replace('\\', '/') + "\"\n");
            
            //Server infinite loop and wait for clients to connect
            while (true) {
                Socket socket = serverSocket.accept(); //Accept Client connection
                event("Connection accepted " + socket.getInetAddress() + ":" + socket.getPort());
                
                //Create a new thread and handle the connection
                ClientThread ct = new ClientThread(socket);
                list.add(ct);
                ct.start();
            }
            
        } catch (Exception e) {
            event("Exception in starting server: " + e);
        }
    }
    
    public static void main(String[] args) {
        try {
            //Read in port from command line
            port = Integer.parseInt(args[0]);
            System.out.println("Port number set to " + port + "\n");
            
        } catch (Exception e) {
            //Default to port 8080 if there's a parsing error
            port = 8080;
            System.out.println("Error parsing port number; " +
                    "using port number 8080\n");
        }
         
        TextServer server = new TextServer(port);
        server.start();
    }
 
    //Class to handle the Clients (each in their own thread)
    class ClientThread extends Thread {
        Socket socket;
        ObjectInputStream in;
        ObjectOutputStream out;
        
        //Client info
        int id;
        String username;
        String date; //Timestamp of when the client has connected
        
        //Messages
        ChatMessage cm;
        
        //Creates input/output object streams and reads in the username
        //Creates directory named "[username]"
        //Disconnects user if username is already in use (directory already exists)
        ClientThread(Socket socket) {
            id = ++uniqueID;
            this.socket = socket;

            //Create both input/output data streams
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in  = new ObjectInputStream(socket.getInputStream());
                
                //Read the username
                username = (String) in.readObject();
                
                //Check to see if the username is already in use
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).username.equals(this.username)) {
                        writeMsg("Username already taken");
                        username = username + id; //update the username to the username plus the unique id number
                        writeMsg("Username is now: " + username);
                    }
                }
                
                //Create directory for the client's files
                if (!new File(username).exists()) {
                    new File(username).mkdir();
                    event("Directory created");
                }
                
                event(username + " has connected");
                
            } catch (IOException e) {
                event("Exception creating new input/output streams");
                remove(id);
                close();
                return;
                
            } catch (ClassNotFoundException e) {
                event("¯\\_(ツ)_/¯");
            }

            date = new Date().toString() + "\n";
        }
        
        @Override
        public void run() {
            boolean loggedIn = true;
            
            while(loggedIn) {
                //Read in from the input stream (which is a ChatMessage object)
                try {
                    cm = (ChatMessage) in.readObject();
                    
                } catch (IOException e) {
                    event(username + " Exception Reading Streams: " + e);
                    break;			
                } catch(ClassNotFoundException e) {
                    break;
                }
                
                //Get messaage part of the ChatMessage object
                String message = cm.getMessage();

                //Switch on the type of message
                switch(cm.getType()) {
                    case ChatMessage.MESSAGE:
                        processMessage(message);
                        break;

                    case ChatMessage.LOGOUT:
                        event(username + " disconnected with a LOGOUT message.");
                        loggedIn = false;
                        break;

                    case ChatMessage.WHOISIN:
                        //Print all Clients that are connected to the server
                        for(int i = 0; i < list.size(); ++i) {
                            ClientThread ct = list.get(i);
                            writeMsg((i+1) + ") " + ct.username + " since " + ct.date);
                        }
                        break;
                }
            }
            
            //Client has decided to log out
            remove(id); //Remove client from the arrayList in the Server
            close(); //Close down input/output streams and socket
            deleteDirectory(new File(username));
        }

        //SHUT DOWN EVERYTHING
        private void close() {
            try {
                if(out != null) out.close();
            } catch(Exception e) {
                /* ¯\_(ツ)_/¯ */
            }
            
            try {
                if(in != null) in.close();
            } catch(Exception e) {
                /* ¯\_(ツ)_/¯ */
            }
            
            try {
                if(socket != null) socket.close();
            } catch (Exception e) {
                /* ¯\_(ツ)_/¯ */
            }
        }
        
        //Delete the client's directory
        private void deleteDirectory(File file) {
            File[] contents = file.listFiles();
            if (contents != null) {
                for (File f : contents) {
                    deleteDirectory(f);
                }
            }
            file.delete();
            event(username + "'s Directory Deleted");
        }
        
        //Write message to the Client
        private boolean writeMsg(String msg) {
            //If Client is not connected, SHUT DOWN EVERYTHING
            if(!socket.isConnected()) {
                close();
                return false;
            }
            
            try {
                out.writeObject(msg);
            } catch(IOException e) {
                event("Error sending message to " + username);
            }
            
            return true;
        }
        
        //Process message from client
        private void processMessage(String message) {
            if (message.toLowerCase().startsWith("SEND /".toLowerCase())) {
                //Prepare to receive the file /fileName
                int index = Math.max(message.lastIndexOf("/"), message.lastIndexOf("\\"));
                String fileName = message.substring(index + 1);
                receiveFile(fileName);
                
            } else if (message.toLowerCase().startsWith("GET /".toLowerCase())) {
                //Send the /directory/fileName to user
                String fileName = message.substring(5);
                writeMsg("Receiving " + fileName);
                
                if (new File(fileName).exists()) {
                    sendFile(fileName);
                } else {
                    writeMsg("File Not Found on Server: " + fileName);
                    event("File Not Found on Server: " + fileName);
                }
                
            } else if (message.toLowerCase().startsWith("BROADCAST".toLowerCase())){
                //Breadcast message to everyone
                broadcast(message.substring(9), username);
                
            } else {
                writeMsg(" ¯\\_(ツ)_/¯");
            }
        }
        
        //Receive File from Client
        private synchronized void receiveFile(String fileName) {
            try {
                //Place file in /username/directory
                File f = new File(username + "/" + fileName);
                byte[] content = (byte[]) in.readObject();
                Files.write(f.toPath(), content);

                event("File Received: " + fileName);
                writeMsg("Server Successfully Received File: \"" + fileName + "\"");

            } catch (IOException | ClassNotFoundException | ClassCastException ex) {
                event("Error receiving file " + fileName);
                writeMsg("Error Receiving File: \"" + fileName + "\"");
            }
        }
        
        //Send file to Client
        private synchronized void sendFile(String fileName) {
            try {
                File f = new File(fileName);
                byte[] content = Files.readAllBytes(f.toPath());
                out.writeObject(content);
                
                event("File successfully sent: " + fileName);

            } catch (FileNotFoundException e ) {
                event("File Not Found: " + fileName);
                writeMsg("File Not Found: \"" + fileName + "\"");

            } catch (IOException e) {
                event("Error sending file " + fileName);
                writeMsg("Error Sending File: \"" + fileName + "\"");
            }
        }
    }
}
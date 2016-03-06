/**
 * COEN 317
 * Distributed Text Editor GUI
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import static javax.swing.JFrame.EXIT_ON_CLOSE;

public class TextEditor extends JFrame implements ActionListener {
    private static final long serialVersionUID = 1L;
    
    public JLabel label; //"Username" (logged out) or "Enter Message" (logged in)
    private JTextField tf; //For Username (logged out) or Message (logged in)
    private JTextField tfServer, tfPort; //For Server and Port
    private JButton login, logout, whoIsIn, info; //Buttons
    private JTextArea ta; //Text editing area
    
    private MenuBar menuBar;
    private Menu fileMenu;
    private FileDialog fileDialog;
    
    private boolean connected;
    private TextClient client;
    private int defaultPort;
    private String defaultHost;
    
    private String fileName;
    private String serverFile;
    public String username;

    TextEditor(String host, int port) {
        super("Text Editor");
        defaultPort = port;
        defaultHost = host;

        JPanel northPanel = new JPanel(new GridLayout(3, 1));
        JPanel serverAndPort = new JPanel(new GridLayout(1, 5, 1, 3));
        
        //Two JTextField with default value for server address and port number
        tfServer = new JTextField(host);
        tfServer.setEditable(true);
        
        tfPort = new JTextField("" + port);
        tfPort.setHorizontalAlignment(SwingConstants.RIGHT);
        tfPort.setEditable(true);

        serverAndPort.add(new JLabel("Server Address:  "));
        serverAndPort.add(tfServer);
        serverAndPort.add(new JLabel("Port Number:  "));
        serverAndPort.add(tfPort);
        serverAndPort.add(new JLabel(""));
        
        //Add Server and Port field to the GUI
        northPanel.add(serverAndPort);

        //Label and the TextField
        label = new JLabel("Enter your username below", SwingConstants.CENTER);
        northPanel.add(label);
        tf = new JTextField("Anonymous");
        tf.setBackground(Color.WHITE);
        northPanel.add(tf);
        add(northPanel, BorderLayout.NORTH);

        //CenterPanel which is text area
        ta = new JTextArea(80, 80);
        JPanel centerPanel = new JPanel(new GridLayout(1,1));
        centerPanel.add(new JScrollPane(ta));
        ta.setEditable(true);
        add(centerPanel, BorderLayout.CENTER);

        //Buttons
        login = new JButton("Login");
        login.addActionListener(this);
        
        logout = new JButton("Logout");
        logout.addActionListener(this);
        logout.setEnabled(false); //Have to be logged in to able to Logout
        
        whoIsIn = new JButton("Who is in");
        whoIsIn.addActionListener(this);
        whoIsIn.setEnabled(false); //Have to logged in to see Who is online
        
        info = new JButton("Info");
        info.addActionListener(this);
        info.setEnabled(false); //Have to logged in to see Info

        JPanel southPanel = new JPanel();
        southPanel.add(login);
        southPanel.add(logout);
        southPanel.add(whoIsIn);
        southPanel.add(info);
        add(southPanel, BorderLayout.SOUTH);
        
        //Menu Bar
        menuBar = new MenuBar();
        setMenuBar(menuBar);
        fileMenu = new Menu("File");
        menuBar.add(fileMenu);
        
        //Menu Bar Options
        fileMenu.add(new MenuItem("New"));
        fileMenu.add(new MenuItem("Open"));
        fileMenu.add(new MenuItem("Save"));
        fileMenu.add(new MenuItem("Save as..."));
        fileMenu.add(new MenuItem("Send File to Server"));
        fileMenu.add(new MenuItem("Get File from Server"));
        fileMenu.add(new MenuItem("Exit"));
        fileMenu.addActionListener(this);
        
        fileDialog = new FileDialog(this);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 800);
        setVisible(true);
        tf.requestFocus();
    }

    //Opens pop up option with message
    void display(String message) {
        JOptionPane.showMessageDialog(this, message);
    }
    
    //Append message to text area
    void append(String message) {
        ta.append(message);
        ta.setCaretPosition(ta.getText().length() - 1);
    }
    
    //Reset everything to defaults
    void connectionFailed() {
        login.setEnabled(true);
        logout.setEnabled(false);
        whoIsIn.setEnabled(false);
        info.setEnabled(false);
        
        label.setText("Enter your username below");
        tf.setText("Anonymous");
        
        tfPort.setText("" + defaultPort);
        tfServer.setText(defaultHost);
        
        tfServer.setEditable(true);
        tfPort.setEditable(true);
        
        tf.removeActionListener(this);
        connected = false;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object o = e.getSource();
        
        //File Menu option selected
        if (o == fileMenu) {
            String menuSelection = e.getActionCommand();
            switch (menuSelection) {
                case "New":
                    ta.setText("");
                    fileName = null;
                    break;

                case "Open":
                    fileDialog.setVisible(true);
                    fileName = fileDialog.getDirectory() + fileDialog.getFile();
                    if (fileName != null) {
                        displayFile(fileName);
                    }
                    break;

                case "Save":
                    if(fileName == null) {
                        fileDialog.setVisible(true);
                        fileName = fileDialog.getDirectory() + fileDialog.getFile();
                    }
                    
                    if (fileName != null) {
                        saveFile();
                    }
                    break;

                case "Save as...":
                    fileDialog.setVisible(true);
                    fileName = fileDialog.getDirectory() + fileDialog.getFile();
                    
                    if (fileName != null) {
                        saveFile();
                    }
                    break;
                    
                case "Send File to Server":
                    if (connected) {
                        fileDialog.setVisible(true);
                        String file = fileDialog.getDirectory() + fileDialog.getFile();
                        if (file != null) {
                            client.sendMessage(new ChatMessage(ChatMessage.MESSAGE, "SEND /" + file));
                            TextClient.sendFile(file);
                        }
                        
                    } else {
                        display("Please log in first");
                    }
                    break;
                    
                case "Get File from Server":
                    //Save the currently opened file
                    if(fileName != null) {
                        saveFile();
                    }
                    
                    if (connected) {
                        String user = JOptionPane.showInputDialog(this, "From which user:");
                        
                        if (user != null) {
                            String file = JOptionPane.showInputDialog(this, "Enter file name:");
                            
                            if (file != null) {
                                serverFile = user + "/" + file;
                                client.sendMessage(new ChatMessage(ChatMessage.MESSAGE, "GET /" + serverFile));
                            }
                        }
                        
                    } else {
                        display("Please log in first");
                    }
                    break;

                case "Exit":
                    this.dispose();
                    System.exit(0);
            }
            
            return;
        }
        
        //Logout button
        if(o == logout) {
            client.sendMessage(new ChatMessage(ChatMessage.LOGOUT, ""));
            return;
        }
        
        //Who is in button
        if(o == whoIsIn) {
            client.sendMessage(new ChatMessage(ChatMessage.WHOISIN, ""));				
            return;
        }
        
        //Info button
        if(o == info) {
            JOptionPane.showMessageDialog(this, "Username: " + this.username + "\n"
                    + "Enter \"BROADCAST [message]\" to broadcast a message to all users\n"
                    + "All of your files will be deleted off the server when you log out");
            return;
        }

        //Send Message
        if(connected) {
            String msg = tf.getText();
            
            if (msg.toLowerCase().startsWith("SEND /".toLowerCase())) {
                //Send File to Server
                //The file to be sent is the substring starting at index 6
                if (new File(msg.substring(6)).exists()) {
                    client.sendMessage(new ChatMessage(ChatMessage.MESSAGE, msg));
                    TextClient.sendFile(msg.substring(6));
                } else {
                    display("File Not Found: " + msg.substring(6) + "\n");
                }
                
            } else if (msg.toLowerCase().startsWith("GET /".toLowerCase())) {
                //Get File from Server
                client.sendMessage(new ChatMessage(ChatMessage.MESSAGE, msg));
                
            } else {
                //Default send message
                client.sendMessage(new ChatMessage(ChatMessage.MESSAGE, msg));
            }
            
            tf.setText("");
            return;
        }

        //Logging In
        if(o == login) {
            //Start connection request
            username = tf.getText().trim();
            
            //Empty username
            if(username.length() == 0)
                return;
            
            //Empty Server Address
            String server = tfServer.getText().trim();
            if(server.length() == 0)
                return;
            
            //Empty or invalid port numer
            String portNumber = tfPort.getText().trim();
            if(portNumber.length() == 0)
                return;
            
            int port;
            
            try {
                port = Integer.parseInt(portNumber);
            } catch(Exception ex) {
                return;
            }

            //Creating a new Client with GUI
            client = new TextClient(server, port, username, this);
            
            //Check if client has started
            if(!client.start()) return;
            
            this.setTitle(System.getProperty("user.dir").replace('\\', '/') + "/");
            
            tf.setText("");
            label.setText("Hello " + this.username + ". Enter you commands below");
            connected = true;

            //Disable login button
            login.setEnabled(false);
            
            //Enable these buttons
            logout.setEnabled(true);
            whoIsIn.setEnabled(true);
            info.setEnabled(true);
            
            //Disable the Server and Port JTextField
            tfServer.setEditable(false);
            tfPort.setEditable(false);
            
            //Action listener for when the user enter a message
            tf.addActionListener(this);
        }
    }
    
    private void saveFile() {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
            String data = ta.getText();
            bw.write(data);
            bw.close();
            
        } catch(IOException e) {
            display("Exception While Saving File: " + e);
        }
    }
    
    public void displayFile(String file) {
        String line;
        ta.setText("");
        
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            line = br.readLine();
            while(line != null) {
                ta.append(line + '\n');
                line = br.readLine();
            }
            br.close();
            
            fileName = new File(file).getAbsolutePath();
            this.setTitle(fileName);
            
        } catch(IOException e) {
            display("Exception Opening File");
        }
    }

    public static void main(String[] args) {
        TextEditor GUI = new TextEditor("localhost", 8080);
    }
}
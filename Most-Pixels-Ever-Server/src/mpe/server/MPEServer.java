/**
 * The "Most Pixels Ever" Wallserver.
 * This server can accept two values from the command line:
 * -port<port number> Defines the port.  Defaults to 9002
 * -ini<Init file path>  File path to mpeServer.ini.  Defaults to directory of server.
 * @author Shiffman and Kairalla
 *
 */

package mpe.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class MPEServer {

    private ArrayList<Connection> connections = new ArrayList<Connection>();
    private int port;
    private boolean running = false;
    public boolean[] connected;  // When the clients connect, they switch their slot to true
    public boolean[] ready;      // When the clients are ready for next frame, they switch their slot to true
    public boolean allConnected = false;  // When true, we are off and running
    int frameCount = 0;
    private long before;
    
    // Server will add a message to the frameEvent
    public boolean newMessage = false;
    public String message = "";
    
    // Server can send a byte array!
    public boolean newBytes = false;
    public byte[] bytes = null;
    
    // Server can send an int array!
    public boolean newInts = false;
    public int[] ints = null;
    
    public boolean dataload = false;

    public MPEServer(int _screens, int _framerate, int _port) {
        MPEPrefs.setScreens(_screens);
        MPEPrefs.setFramerate(_framerate);
        port = _port;
        out("framerate = " + MPEPrefs.FRAMERATE + ",  screens = " + MPEPrefs.SCREENS + ", verbose = " + MPEPrefs.VERBOSE);
        
        connected = new boolean[MPEPrefs.SCREENS];  // default to all false
        ready = new boolean[MPEPrefs.SCREENS];      // default to all false
    }
    
    public void run() {
        running = true;
        before = System.currentTimeMillis(); // Getting the current time
        ServerSocket frontDoor;
        try {
            frontDoor = new ServerSocket(port);

            System.out.println("Starting server: " + InetAddress.getLocalHost() + "  " + frontDoor.getLocalPort());

            // Wait for connections (could thread this)
            while (running) {
                Socket socket = frontDoor.accept();  // BLOCKING!                       
                System.out.println(socket.getRemoteSocketAddress() + " connected.");
                // Make  and start connection object
                Connection conn = new Connection(socket,this);
                conn.start();
                // Add to list of connections
                connections.add(conn); 
            }
        } catch (IOException e) {
            System.out.println("Zoinks!" + e);
        }
    }
    
    // Synchronize?!!!
    public synchronized void triggerFrame() {
        int desired = (int) ((1.0f / (float) MPEPrefs.FRAMERATE) * 1000.0f);
        long now = System.currentTimeMillis();
        int diff = (int) (now - before);
        if (diff < desired) {
            // Where do we max out a framerate?  Here?
            try {
                long sleepTime = desired-diff;
                if (sleepTime >= 0){
                    Thread.sleep(sleepTime);
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            try {
                long sleepTime = 2;
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        // Reset everything to false
        for (int i = 0; i < ready.length; i++) {
            ready[i] = false;
        }        

        frameCount++;
        
        String send = "G,"+(frameCount-1);
        
        // Adding a data message to the frameEvent
        //substring removes the ":" at the end.
        if (newMessage) send += ":" + message.substring(0, message.length()-1);
        newMessage = false;
        message = "";
        
        if (newBytes) {
          send = "B" + send;
          sendAll(send);
          sendAllBytes();
          newBytes = false;
        } else if (newInts) {
          send = "I" + send;
          sendAll(send);
          sendAllInts();
          newInts = false;
        } else {
          sendAll(send);
        }
        before = System.currentTimeMillis();
    }

    private void print(String string) {
        System.out.println("MPEServer: "+string);

    }

    public synchronized void sendAll(String msg){
        //System.out.println("Sending " + msg + " to clients: " + connections.size());
        for (int i = 0; i < connections.size(); i++){
            Connection conn = connections.get(i);
            conn.send(msg);
        }
    }
    
    public synchronized void sendAllBytes(){
        //System.out.println("Sending " + msg + " to clients: " + connections.size());
        for (int i = 0; i < connections.size(); i++){
            Connection conn = connections.get(i);
            conn.sendBytes(bytes);
        }
    }
    
    public synchronized void sendAllInts(){
        //System.out.println("Sending " + msg + " to clients: " + connections.size());
        for (int i = 0; i < connections.size(); i++){
            Connection conn = connections.get(i);
            conn.sendInts(ints);
        }
    }

    public void killConnection(Connection conn){
        connections.remove(conn);
    }

    boolean allDisconected(){
        if (connections.size() < 1){
            return true;
        } else return false;
    }
    void resetFrameCount(){
        frameCount = 0;
        newMessage = false;
        message = "";
        print ("resetting frame count.");
    }
    public void killServer() {
        running = false;
    }
    
    public static void main(String[] args) {
        // set default values
        int screens = 2;
        int framerate = 30;
        int port = 9002;
        int listenPort = 9003;
        
        boolean help = false;
        
        // see if info is given on the command line
        for (int i = 0; i < args.length; i++) {
        	if (args[i].contains("-screens")) {
                args[i] = args[i].substring(8);
                try{
                    screens = Integer.parseInt(args[i]);
                } catch (Exception e) {
                    out("ERROR: I can't parse the # of screens " + args[i] + "\n" + e);
                    help = true;
                }
            } 
            else if (args[i].contains("-framerate")) {
                args[i] = args[i].substring(10);
                try{
                    framerate = Integer.parseInt(args[i]);
                } catch (Exception e) {
                    out("ERROR: I can't parse the frame rate " + args[i] + "\n" + e);
                    help = true;
                }
            } 
            else if (args[i].contains("-port")) {
                args[i] = args[i].substring(5);
                try {
                    port = Integer.parseInt(args[i]);
                } catch (Exception e) {
                    out("ERROR: I can't parse the port number " + args[i] + "\n" + e);
                    help = true;
                }
            }
            else if (args[i].contains("-verbose")) {
                MPEPrefs.VERBOSE = true;
            }
            else {
                help = true;
            }
        }
        
        if (help) {
            // if anything unrecognized is sent to the command line, help is
            // displayed and the server quits
            System.out.println(" * The \"Most Pixels Ever\" server.\n" +
                    " * This server can accept the following parameters from the command line:\n" +
                    " * -screens<number of screens> Total # of expected clients.  Defaults to 2\n" +
                    " * -framerate<framerate> Desired frame rate.  Defaults to 30\n" +
                    " * -port<port number> Defines the port.  Defaults to 9002\n" +
                    " * -verbose Turns debugging messages on.\n" +
                    " * -xml<path to file with XML settings>  Path to initialization file.  Defaults to \"settings.xml\".\n");
            System.exit(1);
        }
        else {
            MPEServer ws = new MPEServer(screens, framerate, port);
            ws.run();
        }
    }
    private static void out(String s){
        System.out.println("MPEServer: "+ s);
    }

    public void drop(int i) {
        connected[i] = false;
        ready[i] = false;
    }

    // synchronize??
    public synchronized void setReady(int clientID) { 
        ready[clientID] = true;
        if (isReady()) triggerFrame();
    }

    // synchronize?
    public synchronized boolean isReady() {
        boolean allReady = true;
        for (int i = 0; i < ready.length; i++){  //if any are false then wait
            if (ready[i] == false) allReady = false;
        }
        return allReady;
    }
}
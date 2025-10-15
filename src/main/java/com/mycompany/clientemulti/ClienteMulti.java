package clientemulti;
 
import java.io.IOException;
import java.net.Socket;
 
public class ClienteMulti {
 
    public static void main(String[] args) {
        Socket s = null;
        try {
            s = new Socket("localhost", 8080);
 
            Thread hiloParaMandar = new Thread(new ParaMandar(s), "sender");
            Thread hiloParaRecibir = new Thread(new ParaRecibir(s), "receiver");
 
            hiloParaMandar.start();
            hiloParaRecibir.start();
 
            
            hiloParaMandar.join();
 
        } catch (Exception e) {
            System.out.println("Error en ClienteMulti: " + e.getMessage());
        } finally {
            if (s != null && !s.isClosed()) {
                try { s.close(); } catch (IOException ignore) {}
            }
        }
    }
}
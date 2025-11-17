package clientemulti;
 
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
 
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
 
        } catch (ConnectException e) {
            System.out.println("Error: No se pudo conectar al servidor. Verifica que el servidor esté ejecutándose.");
        } catch (UnknownHostException e) {
            System.out.println("Error: No se pudo encontrar el host especificado.");
        } catch (InterruptedException e) {
            System.out.println("Error: La conexión fue interrumpida.");
        } catch (IOException e) {
            System.out.println("Error de entrada/salida al intentar establecer la conexión.");
        } catch (Exception e) {
            System.out.println("Error inesperado al iniciar el cliente.");
        } finally {
            if (s != null && !s.isClosed()) {
                try { 
                    s.close(); 
                } catch (IOException ignore) {}
            }
        }
    }
}
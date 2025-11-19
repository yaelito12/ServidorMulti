package clientemulti; 
 
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
 
public class ParaMandar implements Runnable {
    private final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
    private final DataOutputStream salida;
    private final Socket socket;
 
    public ParaMandar(Socket s) throws IOException {
        this.socket = s;
        this.salida = new DataOutputStream(s.getOutputStream());
    }
 
    @Override
    public void run() {
        try {
            while (true) {
                String mensaje = teclado.readLine();
                if (mensaje == null) break;
 
                salida.writeUTF(mensaje);
                salida.flush();
 
                if ("/salir".equalsIgnoreCase(mensaje)) {
                    System.out.println("Cerrando conexión...");
                    socket.close();
                    break;
                }
            }
        } catch (SocketException e) {
            System.out.println("Error: La conexión con el servidor se ha perdido.");
        } catch (EOFException e) {
            System.out.println("Error: El servidor ha cerrado la conexión inesperadamente.");
        } catch (IOException e) {
            System.out.println("Error: No se pudo enviar el mensaje. Verifica la conexión con el servidor.");
        } catch (Exception e) {
            System.out.println("Error inesperado al intentar enviar el mensaje.");
        } finally {
            try {
                if (teclado != null) teclado.close();
                if (salida != null) salida.close();
            } catch (IOException ignore) {}
        }
    }
}
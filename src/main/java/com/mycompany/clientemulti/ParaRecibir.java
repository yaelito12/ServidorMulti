package clientemulti;
 
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
 
public class ParaRecibir implements Runnable {
    private final DataInputStream entrada;
 
    public ParaRecibir(Socket s) throws IOException {
        this.entrada = new DataInputStream(s.getInputStream());
    }
 
    @Override
    public void run() {
        try {
            while (true) {
                String mensaje = entrada.readUTF();
                System.out.println(mensaje);
            }
        } catch (IOException e) {
            System.out.println("Conexión cerrada.");
        } finally {
            try { entrada.close(); } catch (IOException ignored) {}
        }
    }
}
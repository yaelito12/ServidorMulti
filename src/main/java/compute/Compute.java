package compute;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Compute extends Remote {
     T executeTask(Task t) throws RemoteException;
}
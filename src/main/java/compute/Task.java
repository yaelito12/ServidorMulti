package compute;

import java.io.Serializable;

public interface Task extends Serializable {
    T execute();
}
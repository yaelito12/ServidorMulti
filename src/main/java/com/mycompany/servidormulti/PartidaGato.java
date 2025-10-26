package com.mycompany.servidormulti;

public class PartidaGato {
    private final String jugador1;
    private final String jugador2;
    private final char simboloJ1;
    private final char simboloJ2;
    private String turnoActual;
    private char[][] tablero;
    private boolean terminado;
    private String ganador;
    
    public PartidaGato(String jugador1, String jugador2, boolean empiezaJ1) {
        this.jugador1 = jugador1;
        this.jugador2 = jugador2;
        this.simboloJ1 = 'X';
        this.simboloJ2 = 'O';
        this.turnoActual = empiezaJ1 ? jugador1 : jugador2;
        this.tablero = new char[3][3];
        this.terminado = false;
        this.ganador = null;
        inicializarTablero();
    }
    
    
    private void inicializarTablero() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                tablero[i][j] = '-';
            }
        }
    }
    
    public String getJugador1() {
        return jugador1;
    }
    
    public String getJugador2() {
        return jugador2;
    }
    
    public String getTurnoActual() {
        return turnoActual;
    }
    
    public boolean esJugadorEnPartida(String jugador) {
        return jugador.equals(jugador1) || jugador.equals(jugador2);
    }
    
    public boolean isTerminado() {
        return terminado;
    }
    
    public String getGanador() {
        return ganador;
    }
    
    public String getOponente(String jugador) {
        if (jugador.equals(jugador1)) return jugador2;
        if (jugador.equals(jugador2)) return jugador1;
        return null;
    }
    
    public String obtenerTableroTexto() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n   1   2   3\n");
        for (int i = 0; i < 3; i++) {
            sb.append((i + 1)).append("  ");
            for (int j = 0; j < 3; j++) {
                sb.append(tablero[i][j]);
                if (j < 2) sb.append(" | ");
            }
            sb.append("\n");
            if (i < 2) sb.append("  -----------\n");
        }
        return sb.toString();
    }
    
    public synchronized boolean realizarMovimiento(String jugador, int fila, int columna) {
        if (terminado) return false;
        if (!jugador.equals(turnoActual)) return false;
        if (fila < 0 || fila > 2 || columna < 0 || columna > 2) return false;
        if (tablero[fila][columna] != '-') return false;
        
        char simbolo = jugador.equals(jugador1) ? simboloJ1 : simboloJ2;
        tablero[fila][columna] = simbolo;
        
        if (verificarGanador(simbolo)) {
            terminado = true;
            ganador = jugador;
        } else if (tableroLleno()) {
            terminado = true;
            ganador = "EMPATE";
        } else {
            turnoActual = jugador.equals(jugador1) ? jugador2 : jugador1;
        }
        
        return true;
    }
    
    private boolean verificarGanador(char simbolo) {
             
        for (int i = 0; i < 3; i++) {
            if (tablero[i][0] == simbolo && tablero[i][1] == simbolo && tablero[i][2] == simbolo) {
                return true;
            }
        }
        
       
        for (int j = 0; j < 3; j++) {
            if (tablero[0][j] == simbolo && tablero[1][j] == simbolo && tablero[2][j] == simbolo) {
                return true;
            }
        }
        
        
        if (tablero[0][0] == simbolo && tablero[1][1] == simbolo && tablero[2][2] == simbolo) {
            return true;
        }
        if (tablero[0][2] == simbolo && tablero[1][1] == simbolo && tablero[2][0] == simbolo) {
            return true;
        }
        
        return false;
    }
    
    private boolean tableroLleno() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (tablero[i][j] == '-') {
                    return false;
                }
            }
        }
        return true;
    }
    
    public void abandonar(String jugador) {
        terminado = true;
        ganador = getOponente(jugador);
    }
    
    public char getSimbolo(String jugador) {
        if (jugador.equals(jugador1)) return simboloJ1;
        if (jugador.equals(jugador2)) return simboloJ2;
        return ' ';
    }
}

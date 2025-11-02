package com.mycompany.servidormulti;

import java.io.*;
import java.net.Socket;
import java.util.Optional;

public class UnCliente implements Runnable {
    private static final int MENSAJES_GRATUITOS = 3;
    private static final String PREFIJO_INVITADO = "invitado_";
    private static final String GRUPO_PREDETERMINADO = "Todos";
    
    private final DataOutputStream salida;
    private final DataInputStream entrada;
    private final Socket socket;
    private String nombreCliente;
    private boolean autenticado;
    private int mensajesEnviados;
    private String grupoActual;

    public UnCliente(Socket socket) throws IOException {
        this.socket = socket;
        this.salida = new DataOutputStream(socket.getOutputStream());
        this.entrada = new DataInputStream(socket.getInputStream());
        this.nombreCliente = null;
        this.autenticado = false;
        this.mensajesEnviados = 0;
        this.grupoActual = GRUPO_PREDETERMINADO;
    }

    @Override
    public void run() {
        try {
            inicializarCliente();
            procesarComandos();
        } catch (IOException ex) {
            System.out.println(nombreCliente + " se desconectó.");
        } finally {
            manejarDesconexion();
        }
    }
    
    // ==================== PROCESAMIENTO DE COMANDOS ====================
    
    private void procesarComandos() throws IOException {
        while (true) {
            String mensaje = entrada.readUTF();
            
            ComandoHandler handler = obtenerHandlerComando(mensaje);
            if (handler.ejecutar()) continue;
            
            if (!verificarLimiteMensajes()) continue;
            
            procesarMensajeRegular(mensaje);
        }
    }
    
    private ComandoHandler obtenerHandlerComando(String mensaje) {
        String cmd = mensaje.toLowerCase();
        
        if (cmd.equals("registrar")) return () -> { registrarUsuario(); return true; };
        if (cmd.equals("login")) return () -> { iniciarSesion(); return true; };
        if (cmd.equals("logout")) return () -> { cerrarSesion(); return true; };
        if (cmd.equals("help")) return () -> { mostrarAyuda(); return true; };
        if (cmd.equals("@") || cmd.equals("privado")) return () -> { mostrarUsuariosYEnviarMensaje(); return true; };
        if (cmd.equals("bloquear")) return () -> { mostrarUsuariosYBloquear(); return true; };
        if (cmd.equals("desbloquear")) return () -> { mostrarBloqueadosYDesbloquear(); return true; };
        if (cmd.equals("misbloqueados") || cmd.equals("mis bloqueados")) return () -> { mostrarMisBloqueados(); return true; };
        if (cmd.equals("creargrupo")) return () -> { crearGrupo(); return true; };
        if (cmd.equals("eliminargrupo")) return () -> { eliminarGrupo(); return true; };
        if (cmd.equals("unirse")) return () -> { unirseAGrupo(); return true; };
        if (cmd.equals("salirgrupo")) return () -> { salirDeGrupo(); return true; };
        if (cmd.equals("grupos")) return () -> { mostrarGruposDisponibles(); return true; };
        if (cmd.equals("misgrupos")) return () -> { mostrarMisGrupos(); return true; };
        if (cmd.equals("miembros")) return () -> { mostrarMiembrosGrupo(); return true; };
        if (cmd.equals("cambiargrupo")) return () -> { cambiarGrupoActivo(); return true; };
        if (cmd.equals("grupoactual")) return () -> { mostrarGrupoActual(); return true; };
        if (cmd.equals("gato") || cmd.equals("jugar")) return () -> { invitarAJugarGato(); return true; };
        if (cmd.equals("aceptar")) return () -> { aceptarInvitacionGato(); return true; };
        if (cmd.equals("rechazar")) return () -> { rechazarInvitacionGato(); return true; };
        if (cmd.equals("partidas")) return () -> { mostrarPartidasActivas(); return true; };
        if (cmd.equals("rendirse")) return () -> { rendirseEnPartida(); return true; };
        if (cmd.equals("ranking")) return () -> { mostrarRankingGeneral(); return true; };
        if (cmd.equals("vs") || cmd.equals("estadisticas")) return () -> { mostrarEstadisticasVs(); return true; };
        if (esMovimientoGato(mensaje)) return () -> { realizarMovimientoGato(esFormatoSimple(mensaje) ? "jugar " + mensaje : mensaje); return true; };
        
        return () -> false;
    }
    
    private boolean esMovimientoGato(String mensaje) {
        return mensaje.toLowerCase().startsWith("jugar ") || mensaje.matches("^[1-3]\\s+[1-3]$");
    }
    
    private boolean esFormatoSimple(String mensaje) {
        return mensaje.matches("^[1-3]\\s+[1-3]$");
    }
    
    private boolean verificarLimiteMensajes() throws IOException {
        boolean limiteAlcanzado = !autenticado && mensajesEnviados >= MENSAJES_GRATUITOS;
        if (limiteAlcanzado) {
            salida.writeUTF("[SISTEMA]: Has alcanzado el límite de 3 mensajes.");
            salida.writeUTF("[SISTEMA]: Escribe 'registrar' para crear una cuenta o 'login' para iniciar sesión.");
        }
        return !limiteAlcanzado;
    }
    
    private void procesarMensajeRegular(String mensaje) throws IOException {
        System.out.println("[" + nombreCliente + " en " + grupoActual + "]: " + mensaje);
        actualizarContadorMensajes();
        
        obtenerPartidaActiva()
            .map(partida -> { enviarMensajeEnPartidaSafe(mensaje, partida); return true; })
            .orElseGet(() -> { enviarMensajeAGrupoSafe(mensaje); return true; });
    }
    
    private void actualizarContadorMensajes() throws IOException {
        if (autenticado) return;
        
        mensajesEnviados++;
        int restantes = MENSAJES_GRATUITOS - mensajesEnviados;
        String mensajeSistema = restantes > 0 
            ? "[SISTEMA]: Mensaje enviado. Te quedan " + restantes + " mensajes."
            : "[SISTEMA]: Has usado tus 3 mensajes gratuitos. Escribe 'registrar' o 'login' para continuar.";
        salida.writeUTF(mensajeSistema);
    }
    
    private Optional<PartidaGato> obtenerPartidaActiva() {
        return ServidorMulti.obtenerPartidasDeJugador(nombreCliente).stream()
            .filter(p -> !p.isTerminado())
            .findFirst();
    }
    
    private void enviarMensajeEnPartidaSafe(String mensaje, PartidaGato partida) {
        try {
            enviarMensajeEnPartida(mensaje, partida);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void enviarMensajeEnPartida(String mensaje, PartidaGato partida) throws IOException {
        String oponente = partida.getOponente(nombreCliente);
        Optional.ofNullable(ServidorMulti.clientes.get(oponente))
            .ifPresent(cliente -> enviarSafe(cliente, "[CHAT-PARTIDA] " + nombreCliente + ": " + mensaje));
        salida.writeUTF("[CHAT-PARTIDA] Tú: " + mensaje);
    }
    
    private void enviarMensajeAGrupoSafe(String mensaje) {
        try {
            enviarMensajeAGrupo(mensaje);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void enviarMensajeAGrupo(String mensaje) throws IOException {
        if (!autenticado) {
            salida.writeUTF("[ERROR]: Debes estar autenticado para enviar mensajes a grupos.");
            return;
        }
        
        if (!ServidorMulti.esMiembroDeGrupo(nombreCliente, grupoActual)) {
            salida.writeUTF("[ERROR]: No eres miembro del grupo '" + grupoActual + "'.");
            return;
        }
        
        // Guardar mensaje en base de datos
        long idMensaje = ServidorMulti.guardarMensajeGrupo(grupoActual, nombreCliente, mensaje);
        
        // Marcar como leído para el remitente
        if (idMensaje > 0) {
            ServidorMulti.actualizarUltimoMensajeLeido(nombreCliente, grupoActual, idMensaje);
        }
        
        // Enviar mensaje solo a miembros que están en el mismo grupo activo
        String mensajeCompleto = "[" + grupoActual + "] " + nombreCliente + ": " + mensaje;
        java.util.List<String> miembros = ServidorMulti.obtenerMiembrosGrupo(grupoActual);
        
        for (String miembro : miembros) {
            if (!miembro.equals(nombreCliente)) {
                UnCliente cliente = ServidorMulti.clientes.get(miembro);
                if (cliente != null && !estaEnPartidaActiva(miembro)) {
                    // Solo enviar mensaje completo si está en el mismo grupo
                    if (cliente.grupoActual.equals(grupoActual)) {
                        enviarSafe(cliente, mensajeCompleto);
                        // Marcar como leído si está en el mismo grupo
                        if (idMensaje > 0) {
                            ServidorMulti.actualizarUltimoMensajeLeido(miembro, grupoActual, idMensaje);
                        }
                    } else {
                        // Solo notificar que hay un nuevo mensaje
                        enviarSafe(cliente, "[NOTIFICACIÓN]: Nuevo mensaje en '" + grupoActual + "'");
                    }
                }
            }
        }
    }
    
    private void enviarSafe(UnCliente cliente, String mensaje) {
        try {
            cliente.salida.writeUTF(mensaje);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // ==================== INICIALIZACIÓN ====================
    
 private void inicializarCliente() throws IOException {
    enviarMensajeBienvenida();
    nombreCliente = PREFIJO_INVITADO + System.currentTimeMillis();
    ServidorMulti.registrarCliente(nombreCliente, this);
}

private void enviarMensajeBienvenida() throws IOException {
    salida.writeUTF("=== BIENVENIDO AL CHAT ===");
    salida.writeUTF("Puedes enviar 3 mensajes de prueba antes de registrarte.");
    salida.writeUTF("Escribe 'registrar' o 'login' cuando quieras autenticarte.");
    salida.writeUTF("Escribe 'logout' para cerrar sesión.");
    salida.writeUTF("Escribe 'help' para ver todos los comandos disponibles.");
}

    private boolean estaEnPartidaActiva(String nombreJugador) {
        return ServidorMulti.obtenerPartidasDeJugador(nombreJugador).stream()
            .anyMatch(p -> !p.isTerminado());
    }
    
    private void cambiarNombreCliente(String nuevoNombre) {
        ServidorMulti.clientes.remove(nombreCliente);
        nombreCliente = nuevoNombre;
        ServidorMulti.registrarCliente(nombreCliente, this);
    }
    
    private void notificarATodos(String mensaje, UnCliente remitente) {
        ServidorMulti.clientes.values().stream()
            .filter(cliente -> cliente != remitente && !estaEnPartidaActiva(cliente.nombreCliente))
            .forEach(cliente -> enviarSafe(cliente, "[SISTEMA]: " + mensaje));
    }
    
    // ==================== GETTER PARA GRUPO ACTUAL ====================
    
    public String getGrupoActual() {
        return grupoActual;
    }
    
    // ==================== GESTIÓN DE GRUPOS ====================
    
    private void crearGrupo() throws IOException {
        if (!verificarAutenticacion()) return;
        
        salida.writeUTF("[SISTEMA]: Ingresa el nombre del nuevo grupo:");
        String nombreGrupo = entrada.readUTF().trim();
        
        if (nombreGrupo.isEmpty()) {
            salida.writeUTF("[ERROR]: El nombre del grupo no puede estar vacío.");
            return;
        }
        
        if (nombreGrupo.equalsIgnoreCase("Todos")) {
            salida.writeUTF("[ERROR]: No puedes usar ese nombre de grupo.");
            return;
        }
        
        if (ServidorMulti.existeGrupo(nombreGrupo)) {
            salida.writeUTF("[ERROR]: Ya existe un grupo con ese nombre.");
            return;
        }
        
        if (ServidorMulti.crearGrupo(nombreGrupo, nombreCliente)) {
            salida.writeUTF("[SISTEMA]: ¡Grupo '" + nombreGrupo + "' creado exitosamente!");
            salida.writeUTF("[SISTEMA]: Ya eres miembro de este grupo.");
            salida.writeUTF("[SISTEMA]: Usa 'cambiargrupo' para cambiar a este grupo.");
        } else {
            salida.writeUTF("[ERROR]: No se pudo crear el grupo. Intenta con otro nombre.");
        }
    }
    
    private void eliminarGrupo() throws IOException {
        if (!verificarAutenticacion()) return;
        
        java.util.List<String> misGrupos = ServidorMulti.obtenerMisGrupos(nombreCliente);
        if (misGrupos.isEmpty()) {
            salida.writeUTF("[SISTEMA]: No perteneces a ningún grupo.");
            return;
        }
        
        salida.writeUTF("[SISTEMA]: Tus grupos:");
        for (String grupo : misGrupos) {
            salida.writeUTF("  - " + grupo);
        }
        
        salida.writeUTF("[SISTEMA]: Ingresa el nombre del grupo a eliminar:");
        String nombreGrupo = entrada.readUTF().trim();
        
        if (nombreGrupo.isEmpty()) {
            salida.writeUTF("[SISTEMA]: Operación cancelada.");
            return;
        }
        
        if (nombreGrupo.equalsIgnoreCase("Todos")) {
            salida.writeUTF("[ERROR]: No puedes eliminar el grupo 'Todos'.");
            return;
        }
        
        if (!ServidorMulti.existeGrupo(nombreGrupo)) {
            salida.writeUTF("[ERROR]: El grupo '" + nombreGrupo + "' no existe.");
            return;
        }
        
        if (ServidorMulti.eliminarGrupo(nombreGrupo)) {
            salida.writeUTF("[SISTEMA]: Grupo '" + nombreGrupo + "' eliminado exitosamente.");
            if (grupoActual.equals(nombreGrupo)) {
                grupoActual = GRUPO_PREDETERMINADO;
                salida.writeUTF("[SISTEMA]: Tu grupo actual ahora es: " + GRUPO_PREDETERMINADO);
            }
            
            // Notificar a los miembros online
            notificarEliminacionGrupo(nombreGrupo);
        } else {
            salida.writeUTF("[ERROR]: No se pudo eliminar el grupo.");
        }
    }
    
    private void notificarEliminacionGrupo(String nombreGrupo) {
        ServidorMulti.clientes.values().forEach(cliente -> {
            if (cliente.grupoActual.equals(nombreGrupo) && !cliente.nombreCliente.equals(nombreCliente)) {
                cliente.grupoActual = GRUPO_PREDETERMINADO;
                enviarSafe(cliente, "[SISTEMA]: El grupo '" + nombreGrupo + "' ha sido eliminado.");
                enviarSafe(cliente, "[SISTEMA]: Tu grupo actual ahora es: " + GRUPO_PREDETERMINADO);
            }
        });
    }
    
    private void unirseAGrupo() throws IOException {
        if (!verificarAutenticacion()) return;
        
        java.util.List<String> gruposDisponibles = ServidorMulti.obtenerGruposDisponibles();
        if (gruposDisponibles.isEmpty()) {
            salida.writeUTF("[SISTEMA]: No hay grupos disponibles.");
            return;
        }
        
        salida.writeUTF("[SISTEMA]: === GRUPOS DISPONIBLES ===");
        for (String grupo : gruposDisponibles) {
            salida.writeUTF("  " + grupo);
        }
        
        salida.writeUTF("[SISTEMA]: Ingresa el nombre del grupo:");
        String nombreGrupo = entrada.readUTF().trim();
        
        if (nombreGrupo.isEmpty()) {
            salida.writeUTF("[SISTEMA]: Operación cancelada.");
            return;
        }
        
        if (!ServidorMulti.existeGrupo(nombreGrupo)) {
            salida.writeUTF("[ERROR]: El grupo '" + nombreGrupo + "' no existe.");
            return;
        }
        
        if (ServidorMulti.esMiembroDeGrupo(nombreCliente, nombreGrupo)) {
            salida.writeUTF("[ERROR]: Ya eres miembro de este grupo.");
            return;
        }
        
        if (ServidorMulti.unirseAGrupo(nombreCliente, nombreGrupo)) {
            salida.writeUTF("[SISTEMA]: ¡Te has unido al grupo '" + nombreGrupo + "'!");
            salida.writeUTF("[SISTEMA]: Usa 'cambiargrupo' para cambiar a este grupo.");
            
            // Obtener mensajes no leídos
            int mensajesNoLeidos = ServidorMulti.contarMensajesNoLeidos(nombreCliente, nombreGrupo);
            
            if (mensajesNoLeidos > 0) {
                salida.writeUTF("[SISTEMA]: Tienes " + mensajesNoLeidos + " mensajes nuevos en este grupo.");
            }
        } else {
            salida.writeUTF("[ERROR]: No se pudo unir al grupo.");
        }
    }
    
    private void salirDeGrupo() throws IOException {
        if (!verificarAutenticacion()) return;
        
        java.util.List<String> misGrupos = ServidorMulti.obtenerMisGrupos(nombreCliente);
        if (misGrupos.isEmpty() || (misGrupos.size() == 1 && misGrupos.get(0).equals("Todos"))) {
            salida.writeUTF("[SISTEMA]: No perteneces a ningún grupo (excepto 'Todos').");
            return;
        }
        
        salida.writeUTF("[SISTEMA]: Tus grupos:");
        for (String grupo : misGrupos) {
            if (!grupo.equals("Todos")) {
                salida.writeUTF("  - " + grupo);
            }
        }
        
        salida.writeUTF("[SISTEMA]: Ingresa el nombre del grupo:");
        String nombreGrupo = entrada.readUTF().trim();
        
        if (nombreGrupo.isEmpty()) {
            salida.writeUTF("[SISTEMA]: Operación cancelada.");
            return;
        }
        
        if (nombreGrupo.equalsIgnoreCase("Todos")) {
            salida.writeUTF("[ERROR]: No puedes salir del grupo 'Todos'.");
            return;
        }
        
        if (!ServidorMulti.esMiembroDeGrupo(nombreCliente, nombreGrupo)) {
            salida.writeUTF("[ERROR]: No eres miembro de este grupo.");
            return;
        }
        
        if (ServidorMulti.salirDeGrupo(nombreCliente, nombreGrupo)) {
            salida.writeUTF("[SISTEMA]: Has salido del grupo '" + nombreGrupo + "'.");
            if (grupoActual.equals(nombreGrupo)) {
                grupoActual = GRUPO_PREDETERMINADO;
                salida.writeUTF("[SISTEMA]: Tu grupo actual ahora es: " + GRUPO_PREDETERMINADO);
            }
        } else {
            salida.writeUTF("[ERROR]: No se pudo salir del grupo.");
        }
    }
    
    private void mostrarGruposDisponibles() throws IOException {
        if (!verificarAutenticacion()) return;
        
        java.util.List<String> grupos = ServidorMulti.obtenerGruposDisponibles();
        if (grupos.isEmpty()) {
            salida.writeUTF("[SISTEMA]: No hay grupos disponibles.");
            return;
        }
        
        salida.writeUTF("");
        salida.writeUTF("=== GRUPOS DISPONIBLES ===");
        for (String grupo : grupos) {
            salida.writeUTF(grupo);
        }
        salida.writeUTF("");
        salida.writeUTF("Usa 'unirse' para unirte a un grupo.");
    }
    
    private void mostrarMisGrupos() throws IOException {
        if (!verificarAutenticacion()) return;
        
        java.util.List<String> grupos = ServidorMulti.obtenerMisGrupos(nombreCliente);
        if (grupos.isEmpty()) {
            salida.writeUTF("[SISTEMA]: No perteneces a ningún grupo.");
            return;
        }
        
        salida.writeUTF("");
        salida.writeUTF("=== TUS GRUPOS ===");
        for (String grupo : grupos) {
            int noLeidos = ServidorMulti.contarMensajesNoLeidos(nombreCliente, grupo);
            String indicador = grupo.equals(grupoActual) ? " [ACTIVO]" : "";
            String mensajes = noLeidos > 0 ? " (" + noLeidos + " nuevos)" : "";
            salida.writeUTF("  - " + grupo + indicador + mensajes);
        }
        salida.writeUTF("");
        salida.writeUTF("Grupo actual: " + grupoActual);
        salida.writeUTF("Usa 'cambiargrupo' para cambiar de grupo.");
    }
    
    private void mostrarMiembrosGrupo() throws IOException {
        if (!verificarAutenticacion()) return;
        
        salida.writeUTF("[SISTEMA]: Ingresa el nombre del grupo:");
        String nombreGrupo = entrada.readUTF().trim();
        
        if (nombreGrupo.isEmpty()) {
            salida.writeUTF("[SISTEMA]: Operación cancelada.");
            return;
        }
        
        if (!ServidorMulti.existeGrupo(nombreGrupo)) {
            salida.writeUTF("[ERROR]: El grupo '" + nombreGrupo + "' no existe.");
            return;
        }
        
        java.util.List<String> miembros = ServidorMulti.obtenerMiembrosGrupo(nombreGrupo);
        if (miembros.isEmpty()) {
            salida.writeUTF("[SISTEMA]: El grupo no tiene miembros.");
            return;
        }
        
        salida.writeUTF("");
        salida.writeUTF("=== MIEMBROS DE '" + nombreGrupo + "' ===");
        for (String miembro : miembros) {
            String estado = ServidorMulti.clientes.containsKey(miembro) ? "[ONLINE]" : "[OFFLINE]";
            salida.writeUTF("  - " + miembro + " " + estado);
        }
        salida.writeUTF("");
        salida.writeUTF("Total: " + miembros.size() + " miembro(s)");
    }
    
  private void cambiarGrupoActivo() throws IOException {
    if (!verificarAutenticacion()) return;
    
    java.util.List<String> misGrupos = ServidorMulti.obtenerMisGrupos(nombreCliente);
    if (misGrupos.isEmpty()) {
        salida.writeUTF("[SISTEMA]: No perteneces a ningún grupo.");
        return;
    }
    
    salida.writeUTF("[SISTEMA]: Tus grupos:");
    for (String grupo : misGrupos) {
        int noLeidos = ServidorMulti.contarMensajesNoLeidos(nombreCliente, grupo);
        String indicador = grupo.equals(grupoActual) ? " [ACTIVO]" : "";
        String mensajes = noLeidos > 0 ? " 📬 (" + noLeidos + " nuevos)" : " ✓";
        salida.writeUTF("  - " + grupo + indicador + mensajes);
    }
    
    salida.writeUTF("[SISTEMA]: Ingresa el nombre del grupo:");
    String nombreGrupo = entrada.readUTF().trim();
    
    if (nombreGrupo.isEmpty()) {
        salida.writeUTF("[SISTEMA]: Operación cancelada.");
        return;
    }
    
    if (!ServidorMulti.esMiembroDeGrupo(nombreCliente, nombreGrupo)) {
        salida.writeUTF("[ERROR]: No eres miembro del grupo '" + nombreGrupo + "'.");
        return;
    }
    
    grupoActual = nombreGrupo;
    salida.writeUTF("[SISTEMA]: Grupo actual cambiado a: " + grupoActual);
    
    java.util.List<BaseDatos.MensajeGrupo> mensajesNoLeidos = 
        ServidorMulti.obtenerMensajesNoLeidos(nombreCliente, grupoActual);
    
    if (!mensajesNoLeidos.isEmpty()) {
        salida.writeUTF("");
        salida.writeUTF("--------------------------------------------");
        salida.writeUTF("   MENSAJES NO LEÍDOS (" + mensajesNoLeidos.size() + ")");
        salida.writeUTF("--------------------------------------------");
        salida.writeUTF("");
        
        for (BaseDatos.MensajeGrupo msg : mensajesNoLeidos) {
            salida.writeUTF("[" + grupoActual + "] " + msg.remitente + ": " + msg.mensaje);
            // Marcar como leído
            ServidorMulti.actualizarUltimoMensajeLeido(nombreCliente, grupoActual, msg.id);
        }
        salida.writeUTF("");
        salida.writeUTF("--------------------------------------------");
        salida.writeUTF("");
    } else {
        salida.writeUTF("[SISTEMA]: ✓ No tienes mensajes nuevos en este grupo.");
    }
}
    private void mostrarGrupoActual() throws IOException {
        if (!verificarAutenticacion()) return;
        
        salida.writeUTF("[SISTEMA]: Tu grupo actual es: " + grupoActual);
        
        int noLeidos = ServidorMulti.contarMensajesNoLeidos(nombreCliente, grupoActual);
        if (noLeidos > 0) {
            salida.writeUTF("[SISTEMA]: Tienes " + noLeidos + " mensaje(s) no leído(s).");
        }
    }
    //Para merge se añadio sistema de grupos
    // ==================== DESCONEXIÓN ====================
    
    private void manejarDesconexion() {
        if (nombreCliente == null) return;
        
        finalizarPartidasActivas();
        ServidorMulti.clientes.remove(nombreCliente);
        notificarATodos(nombreCliente + " se ha desconectado.", this);
        cerrarSocket();
    }
    
    private void finalizarPartidasActivas() {
        ServidorMulti.obtenerPartidasDeJugador(nombreCliente).stream()
            .filter(partida -> !partida.isTerminado())
            .forEach(this::finalizarPartida);
    }
    
    private void finalizarPartida(PartidaGato partida) {
        String oponente = partida.getOponente(nombreCliente);
        partida.abandonar(nombreCliente);
        
        ServidorMulti.registrarResultadoPartida(partida.getJugador1(), partida.getJugador2(), oponente);
        
        notificarVictoriaPorDesconexion(partida);
        ServidorMulti.finalizarPartida(partida.getJugador1(), partida.getJugador2());
    }
    
    private void notificarVictoriaPorDesconexion(PartidaGato partida) {
        String oponente = partida.getOponente(nombreCliente);
        Optional.ofNullable(ServidorMulti.clientes.get(oponente))
            .ifPresent(cliente -> enviarSafe(cliente, "[GATO]: " + nombreCliente + " se desconectó. ¡Has ganado la partida!"));
    }
    
    private void cerrarSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // ==================== MENSAJES PRIVADOS ====================
    
    private void mostrarUsuariosYEnviarMensaje() throws IOException {
        String usuariosOnline = obtenerUsuariosOnline();
        if (validarUsuariosDisponibles(usuariosOnline, "No hay usuarios online.")) return;
        
        salida.writeUTF("[USUARIOS ONLINE]: " + usuariosOnline);
        salida.writeUTF("[SISTEMA]: Escribe: usuario mensaje");
        
        String[] datosMensaje = leerDatosMensajePrivado();
        if (datosMensaje != null) enviarMensajePrivado(datosMensaje[0], datosMensaje[1]);
    }
    
    private String obtenerUsuariosOnline() {
        return ServidorMulti.clientes.keySet().stream()
            .filter(usuario -> !usuario.equals(nombreCliente) && !usuario.startsWith(PREFIJO_INVITADO))
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }
    
    private String[] leerDatosMensajePrivado() throws IOException {
        String[] partes = entrada.readUTF().trim().split(" ", 2);
        if (partes.length < 2) {
            salida.writeUTF("[ERROR]: Formato incorrecto. Usa: usuario mensaje");
            return null;
        }
        return partes;
    }
    
    private void enviarMensajePrivado(String destino, String mensaje) throws IOException {
        if (validarEnvioPrivado(destino)) {
            Optional.ofNullable(ServidorMulti.clientes.get(destino))
                .ifPresentOrElse(
                    cliente -> {
                        enviarSafe(cliente, "[PRIVADO de " + nombreCliente + "]: " + mensaje);
                        enviarSafe(this, "[Mensaje privado enviado a " + destino + "]: " + mensaje);
                        if (!autenticado) mensajesEnviados++;
                    },
                    () -> enviarSafe(this, "[AVISO]: " + destino + " no está conectado en este momento.")
                );
        }
    }
    
    private boolean validarEnvioPrivado(String destino) throws IOException {
        if (!ServidorMulti.usuarios.containsKey(destino)) {
            salida.writeUTF("[ERROR]: Usuario '" + destino + "' no existe.");
            return false;
        }
        if (!autenticado && mensajesEnviados >= MENSAJES_GRATUITOS) {
            salida.writeUTF("[ERROR]: Debes autenticarte para enviar mensajes privados.");
            return false;
        }
        if (ServidorMulti.estasBloqueado(destino, nombreCliente)) {
            salida.writeUTF("[ERROR]: No puedes enviar mensajes a " + destino + " (bloqueado).");
            return false;
        }
        return true;
    }
    
    // ==================== BLOQUEO DE USUARIOS ====================
    
    private void mostrarUsuariosYBloquear() throws IOException {
        if (!verificarAutenticacion()) return;
        
        String usuariosDisponibles = obtenerUsuariosParaBloquear();
        if (validarUsuariosDisponibles(usuariosDisponibles, "No hay usuarios disponibles para bloquear.")) return;
        
        salida.writeUTF("[USUARIOS]: " + usuariosDisponibles);
        salida.writeUTF("[SISTEMA]: Escribe el nombre del usuario:");
        
        String usuarioABloquear = entrada.readUTF().trim();
        if (!usuarioABloquear.isEmpty()) bloquearUsuario(usuarioABloquear);
        else salida.writeUTF("[SISTEMA]: Operación cancelada.");
    }
    
    private String obtenerUsuariosParaBloquear() {
        java.util.List<String> bloqueados = ServidorMulti.obtenerBloqueados(nombreCliente);
        return ServidorMulti.usuarios.keySet().stream()
            .filter(usuario -> !usuario.equals(nombreCliente) && !bloqueados.contains(usuario))
            .map(usuario -> usuario + (ServidorMulti.clientes.containsKey(usuario) ? "[ON]" : "[OFF]"))
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }
    
    private void bloquearUsuario(String usuario) throws IOException {
        if (usuario.equals(nombreCliente)) {
            salida.writeUTF("[ERROR]: No puedes bloquearte a ti mismo.");
            return;
        }
        if (!ServidorMulti.usuarios.containsKey(usuario)) {
            salida.writeUTF("[ERROR]: El usuario '" + usuario + "' no existe.");
            return;
        }
        if (ServidorMulti.estasBloqueado(nombreCliente, usuario)) {
            salida.writeUTF("[ERROR]: Ya tienes bloqueado a " + usuario + ".");
            return;
        }
        
        boolean exito = ServidorMulti.bloquearUsuario(nombreCliente, usuario);
        String mensaje = exito 
            ? "[SISTEMA]: ¡Usuario '" + usuario + "' bloqueado correctamente!"
            : "[ERROR]: No se pudo bloquear al usuario. Intenta de nuevo.";
        salida.writeUTF(mensaje);
        if (exito) System.out.println(nombreCliente + " bloqueó a " + usuario);
    }
    
    private void mostrarBloqueadosYDesbloquear() throws IOException {
        if (!verificarAutenticacion()) return;
        
        java.util.List<String> bloqueados = ServidorMulti.obtenerBloqueados(nombreCliente);
        if (bloqueados.isEmpty()) {
            salida.writeUTF("[SISTEMA]: No tienes usuarios bloqueados.");
            return;
        }
        
        salida.writeUTF("[BLOQUEADOS]: " + formatearListaUsuarios(bloqueados));
        salida.writeUTF("[SISTEMA]: Escribe el nombre del usuario:");
        
        String usuarioADesbloquear = entrada.readUTF().trim();
        if (!usuarioADesbloquear.isEmpty()) desbloquearUsuario(usuarioADesbloquear);
        else salida.writeUTF("[SISTEMA]: Operación cancelada.");
    }
    
    private void desbloquearUsuario(String usuario) throws IOException {
        if (!ServidorMulti.usuarios.containsKey(usuario)) {
            salida.writeUTF("[ERROR]: El usuario '" + usuario + "' no existe.");
            return;
        }
        if (!ServidorMulti.estasBloqueado(nombreCliente, usuario)) {
            salida.writeUTF("[ERROR]: No tienes bloqueado a " + usuario + ".");
            return;
        }
        
        boolean exito = ServidorMulti.desbloquearUsuario(nombreCliente, usuario);
        String mensaje = exito
            ? "[SISTEMA]: ¡Usuario '" + usuario + "' desbloqueado correctamente!"
            : "[ERROR]: No se pudo desbloquear al usuario. Intenta de nuevo.";
        salida.writeUTF(mensaje);
        if (exito) System.out.println(nombreCliente + " desbloqueó a " + usuario);
    }
    
    private void mostrarMisBloqueados() throws IOException {
        if (!verificarAutenticacion()) return;
        
        java.util.List<String> bloqueados = ServidorMulti.obtenerBloqueados(nombreCliente);
        if (bloqueados.isEmpty()) {
            salida.writeUTF("[SISTEMA]: No tienes usuarios bloqueados.");
            return;
        }
        
        salida.writeUTF("[SISTEMA]: === USUARIOS BLOQUEADOS ===");
        for (int i = 0; i < bloqueados.size(); i++) {
            salida.writeUTF((i + 1) + ". " + bloqueados.get(i));
        }
        salida.writeUTF("[SISTEMA]: Total: " + bloqueados.size() + " usuario(s) bloqueado(s)");
    }
    
    private String formatearListaUsuarios(java.util.List<String> usuarios) {
        return usuarios.stream()
            .map(u -> u + (ServidorMulti.clientes.containsKey(u) ? "[ON]" : "[OFF]"))
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }
    
    // ==================== JUEGO DEL GATO ====================
    
    private void invitarAJugarGato() throws IOException {
        if (!verificarAutenticacion()) return;
        
        if (ServidorMulti.tienePartidaActiva(nombreCliente)) {
            salida.writeUTF("[ERROR]: Ya tienes una partida activa. Solo puedes jugar una partida a la vez.");
            salida.writeUTF("[INFO]: Usa 'partidas' para ver tu partida actual o 'rendirse' para abandonarla.");
            return;
        }
    
        mostrarAyudaGato();
        salida.writeUTF("");
        
        String usuariosDisponibles = obtenerUsuariosParaJugar();
        if (validarUsuariosDisponibles(usuariosDisponibles, "No hay usuarios disponibles para jugar.")) return;
        
        salida.writeUTF("[USUARIOS ONLINE]: " + usuariosDisponibles);
        salida.writeUTF("[SISTEMA]: Escribe el nombre del usuario:");
        
        String invitado = entrada.readUTF().trim();
        if (!invitado.isEmpty()) enviarInvitacionJuego(invitado);
        else salida.writeUTF("[SISTEMA]: Operación cancelada.");
    }
    
    private String obtenerUsuariosParaJugar() {
        return ServidorMulti.clientes.keySet().stream()
            .filter(usuario -> !usuario.equals(nombreCliente) && !usuario.startsWith(PREFIJO_INVITADO))
            .map(usuario -> usuario + (ServidorMulti.tienePartidaActiva(usuario) ? "[OCUPADO]" : ""))
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }
    
    private void enviarInvitacionJuego(String invitado) throws IOException {
        if (!validarInvitacionJuego(invitado)) return;
        
        if (ServidorMulti.enviarInvitacionGato(nombreCliente, invitado)) {
            Optional.ofNullable(ServidorMulti.clientes.get(invitado))
                .ifPresent(cliente -> enviarSafe(cliente, "[GATO]: " + nombreCliente + " te invita a jugar. Escribe 'aceptar' o 'rechazar'."));
            salida.writeUTF("[SISTEMA]: Invitación enviada a " + invitado + ".");
            System.out.println(nombreCliente + " invitó a jugar a " + invitado);
        } else {
            salida.writeUTF("[ERROR]: " + invitado + " ya tiene una invitación pendiente.");
        }
    }
    
    private boolean validarInvitacionJuego(String invitado) throws IOException {
        if (invitado.equals(nombreCliente)) {
            salida.writeUTF("[ERROR]: No puedes jugar contigo mismo.");
            return false;
        }
        if (!ServidorMulti.clientes.containsKey(invitado)) {
            salida.writeUTF("[ERROR]: El usuario no está conectado.");
            return false;
        }
        if (ServidorMulti.tienePartidaActiva(invitado)) {
            salida.writeUTF("[ERROR]: " + invitado + " ya está jugando una partida.");
            return false;
        }
        if (ServidorMulti.obtenerPartida(nombreCliente, invitado) != null) {
            salida.writeUTF("[ERROR]: Ya tienes una partida activa con " + invitado + ".");
            return false;
        }
        return true;
    }
    
    private void aceptarInvitacionGato() throws IOException {
        if (!verificarAutenticacion()) return;
        
        if (ServidorMulti.tienePartidaActiva(nombreCliente)) {
            salida.writeUTF("[ERROR]: Ya tienes una partida activa. Solo puedes jugar una partida a la vez.");
            return;
        }
        
        String invitador = ServidorMulti.obtenerInvitador(nombreCliente);
        if (invitador == null) {
            salida.writeUTF("[ERROR]: No tienes invitaciones pendientes.");
            return;
        }
        
        ServidorMulti.eliminarInvitacion(nombreCliente);
        
        if (!ServidorMulti.clientes.containsKey(invitador)) {
            salida.writeUTF("[ERROR]: El invitador ya no está conectado.");
            return;
        }
        
        if (ServidorMulti.crearPartida(invitador, nombreCliente)) {
            iniciarPartida(invitador);
        } else {
            salida.writeUTF("[ERROR]: No se pudo crear la partida.");
        }
    }
    
    private void iniciarPartida(String invitador) throws IOException {
        PartidaGato partida = ServidorMulti.obtenerPartida(invitador, nombreCliente);
        UnCliente clienteInvitador = ServidorMulti.clientes.get(invitador);
        enviarInformacionPartida(partida, invitador, clienteInvitador);
    }
    
    private void enviarInformacionPartida(PartidaGato partida, String oponente, UnCliente clienteOponente) throws IOException {
        String primerJugador = partida.getTurnoActual();
        enviarMensajesInicioPartida(partida, oponente, primerJugador, partida.getSimbolo(nombreCliente));
        
        Optional.ofNullable(clienteOponente)
            .ifPresent(cliente -> {
                try {
                    cliente.enviarMensajesInicioPartida(partida, nombreCliente, primerJugador, partida.getSimbolo(oponente));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
    }
    
    private void enviarMensajesInicioPartida(PartidaGato partida, String oponente, String primerJugador, char simbolo) throws IOException {
        salida.writeUTF("[GATO]: ¡Partida iniciada contra " + oponente + "!");
        salida.writeUTF("[GATO]: Tú eres '" + simbolo + "'");
        salida.writeUTF(primerJugador.equals(nombreCliente) ? "[GATO]: ¡Es TU TURNO!" : "[GATO]: Es el turno de " + oponente);
        salida.writeUTF(partida.obtenerTableroTexto());
        enviarInstruccionesPartida(oponente);
    }
    
    private void enviarInstruccionesPartida(String oponente) throws IOException {
        salida.writeUTF("");
        salida.writeUTF("=== CHAT DE PARTIDA ACTIVADO ===");
        salida.writeUTF("Los mensajes que escribas solo los verá " + oponente);
        salida.writeUTF("NO recibirás mensajes del chat general mientras juegas.");
        salida.writeUTF("Para volver al chat general, finaliza la partida.");
        salida.writeUTF("");
        salida.writeUTF("=== CÓMO JUGAR ===");
        salida.writeUTF("Escribe: fila columna (ejemplo: 1 2)");
        salida.writeUTF("O también: jugar fila columna (ejemplo: jugar 2 3)");
        salida.writeUTF("");
        salida.writeUTF("Coordenadas del tablero:");
        salida.writeUTF("  Fila 1: posiciones 1 1, 1 2, 1 3 (arriba)");
        salida.writeUTF("  Fila 2: posiciones 2 1, 2 2, 2 3 (centro)");
        salida.writeUTF("  Fila 3: posiciones 3 1, 3 2, 3 3 (abajo)");
        salida.writeUTF("");
        salida.writeUTF("Comandos útiles:");
        salida.writeUTF("  partidas - Ver estado del tablero");
        salida.writeUTF("  rendirse - Abandonar partida");
    }
    
    private void rechazarInvitacionGato() throws IOException {
        if (!verificarAutenticacion()) return;
        
        String invitador = ServidorMulti.obtenerInvitador(nombreCliente);
        if (invitador == null) {
            salida.writeUTF("[ERROR]: No tienes invitaciones pendientes.");
            return;
        }
        
        ServidorMulti.eliminarInvitacion(nombreCliente);
        salida.writeUTF("[SISTEMA]: Invitación rechazada.");
        
        Optional.ofNullable(ServidorMulti.clientes.get(invitador))
            .ifPresent(cliente -> enviarSafe(cliente, "[GATO]: " + nombreCliente + " rechazó tu invitación."));
    }
    
    private void mostrarPartidasActivas() throws IOException {
        if (!verificarAutenticacion()) return;
        
        java.util.List<PartidaGato> partidas = ServidorMulti.obtenerPartidasDeJugador(nombreCliente);
        if (partidas.isEmpty()) {
            salida.writeUTF("[SISTEMA]: No tienes partidas activas.");
            return;
        }
        
        salida.writeUTF("[SISTEMA]: === TUS PARTIDAS ===");
        for (int i = 0; i < partidas.size(); i++) {
            mostrarDetallesPartida(partidas.get(i), i + 1);
        }
    }
    
    private void mostrarDetallesPartida(PartidaGato partida, int numero) throws IOException {
        String oponente = partida.getOponente(nombreCliente);
        String estado = partida.isTerminado() ? "TERMINADA" : "EN CURSO";
        String turno = partida.isTerminado() ? "" : " - Turno de: " + partida.getTurnoActual();
        
        salida.writeUTF(numero + ". vs " + oponente + " [" + estado + "]" + turno);
        salida.writeUTF(partida.obtenerTableroTexto());
    }
    
    private void realizarMovimientoGato(String comando) throws IOException {
        if (!verificarAutenticacion()) return;
        
        int[] coordenadas = parsearCoordenadas(comando);
        if (coordenadas == null) return;
        
        Optional.ofNullable(obtenerPartidaConTurno())
            .map(partida -> {
                try {
                    if (partida.realizarMovimiento(nombreCliente, coordenadas[0], coordenadas[1])) {
                        procesarMovimientoExitoso(partida);
                    } else {
                        salida.writeUTF("[ERROR]: Movimiento inválido. La casilla debe estar vacía y en el rango 1-3.");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            })
            .orElseGet(() -> {
                try {
                    salida.writeUTF("[ERROR]: No es tu turno en ninguna partida o no tienes partidas activas.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return false;
            });
    }
    
    private int[] parsearCoordenadas(String comando) throws IOException {
        String[] partes = comando.split("\\s+");
        if (partes.length != 3) {
            salida.writeUTF("[ERROR]: Formato incorrecto. Usa: jugar fila columna (ej: jugar 1 2)");
            return null;
        }
        
        try {
            return new int[]{Integer.parseInt(partes[1]) - 1, Integer.parseInt(partes[2]) - 1};
        } catch (NumberFormatException e) {
            salida.writeUTF("[ERROR]: Fila y columna deben ser números del 1 al 3.");
            return null;
        }
    }
    
    private PartidaGato obtenerPartidaConTurno() {
        return ServidorMulti.obtenerPartidasDeJugador(nombreCliente).stream()
            .filter(p -> !p.isTerminado() && p.getTurnoActual().equals(nombreCliente))
            .findFirst()
            .orElse(null);
    }
    
    private void procesarMovimientoExitoso(PartidaGato partida) throws IOException {
        String oponente = partida.getOponente(nombreCliente);
        UnCliente clienteOponente = ServidorMulti.clientes.get(oponente);
        
        salida.writeUTF("[GATO]: Movimiento realizado.");
        salida.writeUTF(partida.obtenerTableroTexto());
        
        Optional.ofNullable(clienteOponente).ifPresent(cliente -> {
            enviarSafe(cliente, "[GATO]: " + nombreCliente + " realizó un movimiento.");
            enviarSafe(cliente, partida.obtenerTableroTexto());
        });
        
        if (partida.isTerminado()) {
            procesarFinDePartida(partida, oponente, clienteOponente);
        } else {
            notificarCambioTurno(partida, oponente, clienteOponente);
        }
    }
    
    private void procesarFinDePartida(PartidaGato partida, String oponente, UnCliente clienteOponente) throws IOException {
        String ganador = partida.getGanador();
        
        ServidorMulti.registrarResultadoPartida(partida.getJugador1(), partida.getJugador2(), ganador);
        
        enviarResultadoPartida(ganador, oponente, clienteOponente);
        
        salida.writeUTF("[SISTEMA]: Chat de partida desactivado. Tus mensajes ahora van al grupo: " + grupoActual);
        Optional.ofNullable(clienteOponente)
            .ifPresent(cliente -> enviarSafe(cliente, "[SISTEMA]: Chat de partida desactivado. Tus mensajes ahora van al grupo: " + cliente.grupoActual));
        
        ServidorMulti.finalizarPartida(partida.getJugador1(), partida.getJugador2());
    }
    
    private void enviarResultadoPartida(String ganador, String oponente, UnCliente clienteOponente) throws IOException {
        boolean empate = ganador.equals("EMPATE");
        boolean gane = ganador.equals(nombreCliente);
        
        String miMensaje = empate ? "[GATO]: ¡EMPATE! La partida terminó en empate." 
            : gane ? "[GATO]: ¡FELICIDADES! ¡HAS GANADO!" 
            : "[GATO]: Has perdido. " + ganador + " ganó la partida.";
            
        String mensajeOponente = empate ? "[GATO]: ¡EMPATE! La partida terminó en empate."
            : gane ? "[GATO]: Has perdido. " + ganador + " ganó la partida."
            : "[GATO]: ¡FELICIDADES! ¡HAS GANADO!";
        
        salida.writeUTF(miMensaje);
        Optional.ofNullable(clienteOponente).ifPresent(cliente -> enviarSafe(cliente, mensajeOponente));
    }
    
    private void notificarCambioTurno(PartidaGato partida, String oponente, UnCliente clienteOponente) throws IOException {
        salida.writeUTF("[GATO]: Espera el turno de " + oponente);
        Optional.ofNullable(clienteOponente).ifPresent(cliente -> enviarSafe(cliente, "[GATO]: ¡Es TU TURNO!"));
    }
    
    private void rendirseEnPartida() throws IOException {
        if (!verificarAutenticacion()) return;
        
        Optional.ofNullable(obtenerPartidaActiva().orElse(null))
            .ifPresentOrElse(
                partida -> {
                    try {
                        ejecutarRendicion(partida);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                },
                () -> {
                    try {
                        salida.writeUTF("[ERROR]: No tienes partidas activas.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            );
    }
    
    private void ejecutarRendicion(PartidaGato partida) throws IOException {
        String oponente = partida.getOponente(nombreCliente);
        partida.abandonar(nombreCliente);
        
        ServidorMulti.registrarResultadoPartida(partida.getJugador1(), partida.getJugador2(), oponente);
        
        salida.writeUTF("[GATO]: Te has rendido. " + oponente + " gana la partida.");
        salida.writeUTF("[SISTEMA]: Chat de partida desactivado. Volviste al grupo: " + grupoActual);
        
        Optional.ofNullable(ServidorMulti.clientes.get(oponente)).ifPresent(cliente -> {
            enviarSafe(cliente, "[GATO]: " + nombreCliente + " se rindió. ¡Has ganado!");
            enviarSafe(cliente, "[SISTEMA]: Chat de partida desactivado. Volviste al grupo: " + cliente.grupoActual);
        });
        
        ServidorMulti.finalizarPartida(partida.getJugador1(), partida.getJugador2());
        System.out.println(nombreCliente + " se rindió en la partida contra " + oponente);
    }
    
    // ==================== RANKING ====================
    
    private void mostrarRankingGeneral() throws IOException {
        if (!verificarAutenticacion()) return;
        
        java.util.List<String> ranking = ServidorMulti.obtenerRankingGeneral();
        
        if (ranking.isEmpty()) {
            salida.writeUTF("[SISTEMA]: Aún no hay partidas registradas.");
            return;
        }
        
        salida.writeUTF("");
        salida.writeUTF("=== RANKING GENERAL DE JUGADORES ===");
        salida.writeUTF("");
        for (String linea : ranking) {
            salida.writeUTF(linea);
        }
        salida.writeUTF("");
        salida.writeUTF("Sistema de puntos: Victoria = 2 pts | Empate = 1 pt | Derrota = 0 pts");
        salida.writeUTF("");
    }
    
    private void mostrarEstadisticasVs() throws IOException {
        if (!verificarAutenticacion()) return;
        
        String usuariosDisponibles = obtenerUsuariosConEstadisticas();
        
        if (usuariosDisponibles.isEmpty()) {
            salida.writeUTF("[SISTEMA]: No hay otros jugadores con estadísticas.");
            return;
        }
        
        salida.writeUTF("[JUGADORES]: " + usuariosDisponibles);
        salida.writeUTF("[SISTEMA]: Escribe el nombre del jugador:");
        
        String oponente = entrada.readUTF().trim();
        
        if (oponente.isEmpty()) {
            salida.writeUTF("[SISTEMA]: Operación cancelada.");
            return;
        }
        
        if (!ServidorMulti.usuarios.containsKey(oponente)) {
            salida.writeUTF("[ERROR]: El jugador '" + oponente + "' no existe.");
            return;
        }
        
        if (oponente.equals(nombreCliente)) {
            salida.writeUTF("[ERROR]: No puedes ver estadísticas contra ti mismo.");
            return;
        }
        
        mostrarEstadisticasEnfrentamiento(oponente);
    }
    
    private String obtenerUsuariosConEstadisticas() {
        return ServidorMulti.usuarios.keySet().stream()
            .filter(usuario -> !usuario.equals(nombreCliente))
            .map(usuario -> usuario + (ServidorMulti.clientes.containsKey(usuario) ? "[ON]" : "[OFF]"))
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }
    
    private void mostrarEstadisticasEnfrentamiento(String oponente) throws IOException {
        BaseDatos.EstadisticasEnfrentamiento stats = 
            ServidorMulti.obtenerEstadisticasEnfrentamiento(nombreCliente, oponente);
        
        salida.writeUTF("");
        salida.writeUTF("=== ESTADÍSTICAS: " + nombreCliente + " vs " + oponente + " ===");
        salida.writeUTF("");
        
        if (stats.totalPartidas == 0) {
            salida.writeUTF("No hay partidas registradas entre estos jugadores.");
        } else {
            salida.writeUTF("Total de partidas: " + stats.totalPartidas);
            salida.writeUTF("");
            salida.writeUTF(nombreCliente + ": " + stats.victoriasJ1 + " victorias (" + 
                           String.format("%.1f", stats.porcentajeJ1) + "%)");
            salida.writeUTF(oponente + ": " + stats.victoriasJ2 + " victorias (" + 
                           String.format("%.1f", stats.porcentajeJ2) + "%)");
            salida.writeUTF("Empates: " + stats.empates);
            salida.writeUTF("");
            
            if (stats.victoriasJ1 > stats.victoriasJ2) {
                salida.writeUTF("¡Tienes ventaja sobre " + oponente + "!");
            } else if (stats.victoriasJ2 > stats.victoriasJ1) {
                salida.writeUTF(oponente + " tiene ventaja sobre ti.");
            } else {
                salida.writeUTF("Están empatados en victorias.");
            }
        }
        salida.writeUTF("");
    }
    
    // ==================== AYUDA ====================
    
    private void mostrarAyuda() throws IOException {
        salida.writeUTF("=== COMANDOS DISPONIBLES ===");
        salida.writeUTF("");
        salida.writeUTF("AUTENTICACIÓN:");
        salida.writeUTF("  registrar - Crear una nueva cuenta");
        salida.writeUTF("  login - Iniciar sesión");
        salida.writeUTF("  logout - Cerrar sesión");
        salida.writeUTF("");
        salida.writeUTF("GRUPOS:");
        salida.writeUTF("  creargrupo - Crear un nuevo grupo");
        salida.writeUTF("  eliminargrupo - Eliminar un grupo que creaste");
        salida.writeUTF("  unirse - Unirse a un grupo");
        salida.writeUTF("  salirgrupo - Salir de un grupo");
        salida.writeUTF("  grupos - Ver todos los grupos disponibles");
        salida.writeUTF("  misgrupos - Ver tus grupos");
        salida.writeUTF("  miembros - Ver miembros de un grupo");
        salida.writeUTF("  cambiargrupo - Cambiar al grupo activo");
        salida.writeUTF("  grupoactual - Ver tu grupo actual");
        salida.writeUTF("");
        salida.writeUTF("MENSAJES:");
        salida.writeUTF("  @ o privado - Enviar mensaje privado");
        salida.writeUTF("  [mensaje] - Enviar mensaje al grupo actual");
        salida.writeUTF("");
        salida.writeUTF("BLOQUEO:");
        salida.writeUTF("  bloquear - Bloquear usuario");
        salida.writeUTF("  desbloquear - Desbloquear usuario");
        salida.writeUTF("  misBloqueados - Ver usuarios bloqueados");
        salida.writeUTF("");
        salida.writeUTF("JUEGO DEL GATO:");
        salida.writeUTF("  gato - Jugar al gato (tic-tac-toe)");
        salida.writeUTF("  ranking - Ver ranking general de jugadores");
        salida.writeUTF("  vs - Ver estadísticas entre dos jugadores");
        salida.writeUTF("");
        salida.writeUTF("  help - Mostrar esta ayuda");
    }
    
    private void mostrarAyudaGato() throws IOException {
        salida.writeUTF("=== COMANDOS DEL JUEGO GATO ===");
        salida.writeUTF("gato - Invitar a alguien a jugar");
        salida.writeUTF("aceptar - Aceptar invitación de juego");
        salida.writeUTF("rechazar - Rechazar invitación de juego");
        salida.writeUTF("partidas - Ver tus partidas activas");
        salida.writeUTF("");
        salida.writeUTF("=== DURANTE UNA PARTIDA ===");
        salida.writeUTF("Para hacer una jugada usa:");
        salida.writeUTF("  jugar fila columna  (ejemplo: jugar 1 2)");
        salida.writeUTF("  O simplemente:  fila columna  (ejemplo: 2 3)");
        salida.writeUTF("");
        salida.writeUTF("El tablero usa coordenadas así:");
        salida.writeUTF("     1   2   3");
        salida.writeUTF("  1  ■ | ■ | ■    ← Fila 1");
        salida.writeUTF("    -----------");
        salida.writeUTF("  2  ■ | ■ | ■    ← Fila 2");
        salida.writeUTF("    -----------");
        salida.writeUTF("  3  ■ | ■ | ■    ← Fila 3");
        salida.writeUTF("     ↑   ↑   ↑");
        salida.writeUTF("     C1  C2  C3");
        salida.writeUTF("");
        salida.writeUTF("Ejemplos:");
        salida.writeUTF("  '1 1' = esquina superior izquierda");
        salida.writeUTF("  '2 2' = centro del tablero");
        salida.writeUTF("  '3 3' = esquina inferior derecha");
        salida.writeUTF("");
        salida.writeUTF("rendirse - Abandonar la partida actual");
        salida.writeUTF("");
        salida.writeUTF("NOTA: Mientras juegas NO recibirás mensajes del chat general.");
    }
    
    // ==================== AUTENTICACIÓN ====================
    
    private void registrarUsuario() throws IOException {
        salida.writeUTF("[SISTEMA]: === REGISTRO ===");
        salida.writeUTF("[SISTEMA]: Ingresa tu nuevo nombre de usuario:");
        
        String nuevoNombre = entrada.readUTF().trim();
        
        if (!validarNombreUsuario(nuevoNombre) || !validarDisponibilidadNombre(nuevoNombre)) return;
        
        salida.writeUTF("[SISTEMA]: Ingresa tu contraseña:");
        String password = entrada.readUTF().trim();
        
        if (password.isEmpty()) {
            salida.writeUTF("[ERROR]: La contraseña no puede estar vacía.");
            return;
        }
        
        completarRegistro(nuevoNombre, password);
    }
    
    private boolean validarNombreUsuario(String nombre) throws IOException {
        boolean valido = !nombre.isEmpty() && !nombre.contains(" ") && !nombre.contains("@");
        if (!valido) {
            salida.writeUTF("[ERROR]: Nombre inválido. No puede contener espacios ni '@'. Intenta de nuevo escribiendo 'registrar'.");
        }
        return valido;
    }
    
    private boolean validarDisponibilidadNombre(String nombre) throws IOException {
        boolean disponible = ServidorMulti.nombreDisponible(nombre) && !ServidorMulti.usuarios.containsKey(nombre);
        if (!disponible) {
            salida.writeUTF("[ERROR]: El nombre '" + nombre + "' ya está en uso.");
        }
        return disponible;
    }
    
  private void completarRegistro(String nuevoNombre, String password) throws IOException {
    ServidorMulti.registrarUsuario(nuevoNombre, password);
    
    String nombreAnterior = nombreCliente;
    cambiarNombreCliente(nuevoNombre);
    autenticado = true;
    mensajesEnviados = 0;
    grupoActual = GRUPO_PREDETERMINADO;
    
    salida.writeUTF("[SISTEMA]: ¡Registro exitoso! Ahora eres: " + nombreCliente);
    salida.writeUTF("[SISTEMA]: Has sido añadido automáticamente al grupo 'Todos'.");
    salida.writeUTF("[SISTEMA]: Tu grupo actual es: " + grupoActual);
    System.out.println(nombreAnterior + " se registró como: " + nombreCliente);
    
    mostrarMensajesNoLeidosAlEntrar();
    
    // Enviar notificación al grupo "Todos"
    notificarUnionGrupo(GRUPO_PREDETERMINADO);
}
    
    private void iniciarSesion() throws IOException {
        salida.writeUTF("[SISTEMA]: === INICIO DE SESIÓN ===");
        salida.writeUTF("[SISTEMA]: Ingresa tu nombre de usuario:");
        String nombre = entrada.readUTF().trim();
        
        salida.writeUTF("[SISTEMA]: Ingresa tu contraseña:");
        String password = entrada.readUTF().trim();
        
        if (!ServidorMulti.autenticarUsuario(nombre, password)) {
            salida.writeUTF("[ERROR]: Usuario o contraseña incorrectos.");
            return;
        }
        
        if (!ServidorMulti.nombreDisponible(nombre)) {
            salida.writeUTF("[ERROR]: El usuario ya está conectado en otra sesión.");
            return;
        }
        
        completarInicioSesion(nombre);
    }
    private void completarInicioSesion(String nombre) throws IOException {
    String nombreAnterior = nombreCliente;
    cambiarNombreCliente(nombre);
    autenticado = true;
    mensajesEnviados = 0;
    grupoActual = GRUPO_PREDETERMINADO;
    
    salida.writeUTF("[SISTEMA]: ¡Inicio de sesión exitoso! Bienvenido de nuevo, " + nombreCliente);
    salida.writeUTF("[SISTEMA]: Tu grupo actual es: " + grupoActual);
    System.out.println(nombreAnterior + " inició sesión como: " + nombreCliente);
    
    // NUEVO: Mostrar resumen y mensajes no leídos de todos los grupos
    mostrarResumenMensajesNoLeidos();
    
    // NUEVO: Mostrar mensajes no leídos del grupo actual
    mostrarMensajesNoLeidosAlEntrar();
    
    // Notificar conexión solo al grupo actual
    notificarUnionGrupo(grupoActual);
}
   
    private void notificarUnionGrupo(String nombreGrupo) {
        java.util.List<String> miembros = ServidorMulti.obtenerMiembrosGrupo(nombreGrupo);
        String mensaje = "[SISTEMA]: " + nombreCliente + " se ha conectado.";
        
        for (String miembro : miembros) {
            if (!miembro.equals(nombreCliente)) {
                UnCliente cliente = ServidorMulti.clientes.get(miembro);
                if (cliente != null && !estaEnPartidaActiva(miembro)) {
                    // Solo notificar si está en el mismo grupo
                    if (cliente.grupoActual.equals(nombreGrupo)) {
                        enviarSafe(cliente, mensaje);
                    }
                }
            }
        }
    }
    private void mostrarResumenMensajesNoLeidos() throws IOException {
    try {
        java.util.List<String> misGrupos = ServidorMulti.obtenerMisGrupos(nombreCliente);
        int totalNoLeidos = 0;
        java.util.List<String> gruposConMensajes = new java.util.ArrayList<>();
        
        for (String grupo : misGrupos) {
            int noLeidos = ServidorMulti.contarMensajesNoLeidos(nombreCliente, grupo);
            if (noLeidos > 0) {
                totalNoLeidos += noLeidos;
                gruposConMensajes.add(grupo + " (" + noLeidos + ")");
            }
        }
        
        if (totalNoLeidos > 0) {
            salida.writeUTF("");
            salida.writeUTF("--------------------------------------------");
            salida.writeUTF("|   TIENES " + totalNoLeidos + " MENSAJE(S) NUEVO(S)        ║");
            salida.writeUTF("--------------------------------------------");
            
            for (String info : gruposConMensajes) {
                salida.writeUTF("  📁 " + info);
            }
            salida.writeUTF("");
        } else {
            salida.writeUTF("[SISTEMA]: No tienes mensajes nuevos. ✓");
        }
    } catch (Exception e) {
        System.err.println("Error al mostrar resumen: " + e.getMessage());
    }
}

/**
 * Muestra automáticamente los mensajes NO LEÍDOS del grupo actual
 * y los marca como leídos
 */
private void mostrarMensajesNoLeidosAlEntrar() throws IOException {
    try {
        // Obtener mensajes no leídos del grupo actual
        java.util.List<BaseDatos.MensajeGrupo> mensajesNoLeidos = 
            ServidorMulti.obtenerMensajesNoLeidos(nombreCliente, grupoActual);
        
        if (!mensajesNoLeidos.isEmpty()) {
            salida.writeUTF("");
            salida.writeUTF("--------------------------------------------");
            salida.writeUTF("  | MENSAJES NO LEÍDOS EN '" + grupoActual + "' (" + mensajesNoLeidos.size() + ")");
            salida.writeUTF("--------------------------------------------");
            salida.writeUTF("");
            
            // Mostrar cada mensaje no leído
            for (BaseDatos.MensajeGrupo msg : mensajesNoLeidos) {
                salida.writeUTF("[" + grupoActual + "] " + msg.remitente + ": " + msg.mensaje);
                
                // Marcar como leído automáticamente
                ServidorMulti.actualizarUltimoMensajeLeido(nombreCliente, grupoActual, msg.id);
            }
            
            salida.writeUTF("");
            salida.writeUTF("--------------------------------------------");
            salida.writeUTF("  ✓ Todos los mensajes han sido marcados como leídos");
            salida.writeUTF("--------------------------------------------");
            salida.writeUTF("");
            
        } else {
            salida.writeUTF("");
            salida.writeUTF("[SISTEMA]: ✓ No tienes mensajes nuevos en '" + grupoActual + "'.");
            salida.writeUTF("");
        }
    } catch (Exception e) {
        System.err.println("Error al mostrar mensajes no leídos: " + e.getMessage());
        salida.writeUTF("[ERROR]: No se pudieron cargar los mensajes no leídos.");
    }
}
    private void cerrarSesion() throws IOException {
        if (!autenticado) {
            salida.writeUTF("[SISTEMA]: No has iniciado sesión.");
            return;
        }
        
        String nombreAnterior = nombreCliente;
        
        // Notificar desconexión al grupo actual
        notificarDesconexionGrupo(grupoActual);
        
        cambiarNombreCliente(PREFIJO_INVITADO + System.currentTimeMillis());
        autenticado = false;
        mensajesEnviados = 0;
        grupoActual = GRUPO_PREDETERMINADO;
        
        salida.writeUTF("[SISTEMA]: Has cerrado sesión. Ahora eres: " + nombreCliente);
        salida.writeUTF("[SISTEMA]: Tienes 3 mensajes gratuitos. Escribe 'login' para iniciar sesión nuevamente.");
        System.out.println(nombreAnterior + " cerró sesión y ahora es: " + nombreCliente);
    }
    
    private void notificarDesconexionGrupo(String nombreGrupo) {
        java.util.List<String> miembros = ServidorMulti.obtenerMiembrosGrupo(nombreGrupo);
        String mensaje = "[SISTEMA]: " + nombreCliente + " se ha desconectado.";
        
        for (String miembro : miembros) {
            if (!miembro.equals(nombreCliente)) {
                UnCliente cliente = ServidorMulti.clientes.get(miembro);
                if (cliente != null && !estaEnPartidaActiva(miembro)) {
                    // Solo notificar si está en el mismo grupo
                    if (cliente.grupoActual.equals(nombreGrupo)) {
                        enviarSafe(cliente, mensaje);
                    }
                }
            }
        }
    }
    
    // ==================== UTILIDADES ====================
    
    private boolean verificarAutenticacion() throws IOException {
        if (!autenticado) salida.writeUTF("[ERROR]: Debes estar autenticado para usar este comando.");
        return autenticado;
    }
    
    private boolean validarUsuariosDisponibles(String usuarios, String mensajeError) throws IOException {
        if (usuarios.isEmpty()) {
            salida.writeUTF("[SISTEMA]: " + mensajeError);
            return true;
        }
        return false;
    }
    
    // ==================== INTERFAZ FUNCIONAL ====================
    
    @FunctionalInterface
    private interface ComandoHandler {
        boolean ejecutar() throws IOException;
    }
}
package me.hikari.snakeclient.ctl;

import lombok.SneakyThrows;
import me.hikari.snakeclient.data.config.NetConfig;
import me.hikari.snakes.SnakesProto;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.SocketException;

class ListenWorker implements Runnable{
    private static final int MAX_MESSAGE_SIZE = 1000;
    private final NetConfig config;
    private final MulticastSocket socket;
    private final GameManager manager;

    public ListenWorker(GameManager manager, NetConfig config) throws IOException {
        this.manager = manager;
        this.config = config;
        socket = new MulticastSocket(config.getListenPort());
        socket.joinGroup(config.getGroupAddr(), config.getNetIf());
    }

    private void tryDeserializeMessage(DatagramPacket packet){
        SnakesProto.GameMessage.AnnouncementMsg msg = null;
        try{
            msg = SnakesProto.GameMessage.AnnouncementMsg.parseFrom(packet.getData());
        }catch (Exception e){
            //TODO try better, revert serialization
            e.printStackTrace();
            return;
        }
        manager.noteAnnouncement(msg, packet.getAddress());
    }

    @SneakyThrows
    @Override
    public void run() {
        var buf = new byte[MAX_MESSAGE_SIZE];
        while(!Thread.currentThread().isInterrupted()){
            var packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            }catch (SocketException e){
                // normal behaviour for call of this.close();
                return;
            }
            tryDeserializeMessage(packet);
        }
        this.close();
    }

    public void close() throws IOException {
        socket.leaveGroup(config.getGroupAddr(), config.getNetIf());
        socket.close();
    }
}

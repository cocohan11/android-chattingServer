//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

import java.util.ArrayList;

public class Rooms {
    private String Room_name;
    private ArrayList<ClientInfo> arrayList;

    public Rooms(String room_name, ArrayList<ClientInfo> arrayList) {
        this.Room_name = room_name;
        this.arrayList = arrayList;
    }

    public String toString() {
        return "Room_name:" + this.Room_name + ", arrayList: " + this.arrayList;
    }

    public String getRoom_name() {
        return this.Room_name;
    }

    public ArrayList<ClientInfo> getArrayList() {
        return this.arrayList;
    }
}

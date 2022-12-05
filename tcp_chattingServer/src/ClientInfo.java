import com.google.gson.annotations.SerializedName;

import java.net.Socket;

public class ClientInfo {

    public Socket socket; // 안드1개와 서버와 연결되는 소켓
    public String email; // 닉네임
    public String name; // 닉네임
    public String Img;
    public String markerImg; // 합성이미지 서버 주소
    public double Lat; // 위도
    public double Lng; // 경도
    public Object marker; // Marker 클래스가 없다.. 그러면 매번 새로 위치 찍으면 되지 않을까 누구인지만 식별해서 null값넣고.


    public ClientInfo() {
    }

    public ClientInfo(String name) {
        this.name = name;
    }

    public ClientInfo(String name, double lat, double lng) {
        this.name = name;
        this.Lat = lat;
        this.Lng = lng;
    }

    public ClientInfo(Socket socket) {
        this.socket = socket;
    }


    // android iamhere 에서 사용하는 생성자
    public ClientInfo(Socket socket, String name) {
        this.socket = socket;
        this.name = name;
    }

    public ClientInfo(Socket socket, String email, String name, String Img, String markerImg, Object marker, double Lat, double Lng) {
        this.socket = socket;
        this.email = email;
        this.name = name;
        this.Img = Img;
        this.markerImg = markerImg;
        this.marker = marker;
        this.Lat = Lat;
        this.Lng = Lng;
    }

    public String getImg() {
        return Img;
    }

    public void setImg(String img) {
        Img = img;
    }

    public String getEmail() {
        return email;
    }

    public String getMarkerImg() {
        return markerImg;
    }

    public Object getMarker() {
        return marker;
    }

    public void setLat(double lat) {
        Lat = lat;
    }

    public void setLng(double lng) {
        Lng = lng;
    }

    public double getLat() {
        return Lat;
    }

    public double getLng() {
        return Lng;
    }

    public Socket getSocket() {
        return socket;
    }

    public String getName() {
        return name;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    @Override
    public String toString() {
        return "ClientInfo{" +
                "socket=" + socket +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", markerImg='" + markerImg + '\'' +
                ", Lat=" + Lat +
                ", Lng=" + Lng +
                ", marker=" + marker +
                '}';
    }


    //    public String getRoomName() {
//        return roomName;
//    }
//    public ArrayList<Rooms_Info> getArrayList() {
//        return arrayList;
//    }
    //    public int size() {
//        return arrayList.size();
//    }

}

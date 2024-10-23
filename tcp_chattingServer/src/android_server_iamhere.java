import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class android_server_iamhere {


    private int 서버접속자수;
    Map<String, Rooms> Map_rooms = new HashMap(); // 모든 방의 모임. 방번호(외래키)를 key로 가짐
    // 방-클라이언트들-클라-(소켓,이름,img,마커,위경도)
    // JDBC
    Connection conn;
    Statement stmt;
    ResultSet rs;


    //ㅡㅡㅡㅡㅡㅡ
    // 실제 run
    //ㅡㅡㅡㅡㅡㅡ
    public static void main(String[] args) throws SQLException {
        new android_server_iamhere().serverStart();
    }


    public android_server_iamhere() throws SQLException {

        //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
        // DB에 접속하기 위한 재료
        String url = "jdbc:mysql://iamhere.cdf5mmjsw63q.ap-northeast-2.rds.amazonaws.com:3306/iamhere"; // 접속할 서버의 mysql주소
        String userName = "han"; // root말고 han계정으로 들어갔음
        String password = "aaaa";

        // Mysql 연결
        conn = DriverManager.getConnection(url, userName, password);

        // 쿼리작성을 위한 객체 생성
        stmt = conn.createStatement();

        // 쿼리 작성
        String sql = "SELECT Room_no, Room_name, Room_pw FROM Room WHERE Room_activate = 1"; // 입장가능한 방 불러오기

        // 쿼리 수행
        rs = stmt.executeQuery(sql); // 쿼리 날리기
        System.out.println("쿼리결과 : "+rs);
        //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ

    }

    //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
    // 서버시작과 소켓통신중
    //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
    public void serverStart() {

        ServerSocket serverSocket = null; // 연결 요청을 기다리면서 요청오면 수락하는 역할(통신x)
        Socket socket; // 클라이언트에서 넘겨준 소켓, 지금은 null

        try {

            serverSocket = new ServerSocket(8888); // 현재 ip의 8888포트에 서버소켓을 생성한다.
            System.out.println("서버 시작. 대기중....");

            // 모든 방이 담긴 배열 생성하기
            while(rs.next()){

                String RoomNum = String.valueOf(rs.getInt(1));
                String RoomName = rs.getString(2);

                Rooms room = new Rooms(RoomName, new ArrayList<ClientInfo>()); // (방이름, 클라들) // 아직 접속한 클라이언트가 없으니 가짜로 공간만 만듦
                Map_rooms.put(RoomNum, room); // 방 1개씩 담김 (방번호, 방정보)

                System.out.println(RoomNum+"."+RoomName); // 1.딸바 2.토마토 ...

            }
            System.out.println("Map_rooms : "+Map_rooms);
            System.out.println("Map_rooms.size() : "+Map_rooms.size());


            //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
            // 소켓 통신 대기 ~ing
            //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
            while (true) {

                // 클라이언트의 접속을 대기한다.
                System.out.println("소켓 통신 대기 ~ing");
                socket = serverSocket.accept(); // 서버소켓으로 연결이 들어오면 소켓에 넘겨준다. 서버의 소켓과 클라의 소켓이 연결된다.
                System.out.println("["+socket.getInetAddress()+" : "+socket.getPort()+"] 에서 접속하였습니다.");


                // 메시지 전송 처리를 하는 쓰레드 생성 및 실행
                android_server_iamhere.ServerReceiver receiver = new android_server_iamhere.ServerReceiver(socket); // 소켓을 통해 메세지를 전달하는 스레드 시작
                receiver.start();
                System.out.println("receiver.start()");


            }


        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            //서버 소켓 닫기
            if(serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e2) {

                }
            }
        }

    }



    //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
    // 클라이언트 1명과 연결된 소켓통신 스레드 : 한 명의 클라한테 받고 모두에게 보낸다.
    //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
    class ServerReceiver extends Thread{

        private Socket socket; // 클라이언트와 연결될 소켓
        private BufferedReader br;
//        private DataInputStream dis; // 보조스트림. 메모리에 저장된 0,1 상태를 그대로 읽거나 쓴다. 자료형의 크기와 자료형이 유지된다. 자료형을 그대로 갖다쓰기 좋다.
        private String email, name, Img, markerImg, roomNum, roomName; // 유저 정보
        private double Lat, Lng; // 위도와 경도
        private ClientInfo clientInfo;
        private String 목적; // 입장/위치/채팅/퇴장/강제종료(입장은 위에서 종료)
//        private String jsonString;


        //ㅡㅡㅡㅡ
        // 생성자 : ?
        //ㅡㅡㅡㅡ
        public ServerReceiver(Socket socket) { // 클라이언트와 1대1연결된 소켓
            this.socket = socket;
            try {
                //수신용
                br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() { // ServerReceiver 클래스가 시작할 때 실행될 코드
            try {

                // 클라이언트로부터 읽는 최초의 메시지 즉, 대화명을 수신한다.
                email = br.readLine(); // 순서 지키기(선입선출이라 이름먼저)
                name = br.readLine(); // 닉네임
                Img = br.readLine(); // 합성된 마커 이미지
                markerImg = br.readLine(); // 합성된 마커 이미지
                roomNum = br.readLine(); // Unicode String 을 리턴해준다.
                roomName = br.readLine(); // 방이름
                String get위도 = br.readLine(); // 위도 경도
                String get경도 = br.readLine(); // 원래 위치보낼 때 보내려고했는데 그러니까 시기가 안 맞아서 입장할 때 같이 업뎃하기

                try { // 위경도 형변환
                    Lat = Double.parseDouble(get위도); // str to dou
                    Lng = Double.parseDouble(get경도); // str to dou
                } catch (NumberFormatException e) { // 아예 에러나서 예외처리
                    System.out.println("입장 Lat:"+Lat+"/Lng:"+Lng);
                }

                System.out.println("첨 email:"+email+"/name:"+name+"/roomNum:"+roomNum+"/roomName:"+roomName+"\n/Img"+Img+"/markerImg:"+markerImg
                                    +"\n/Lat:"+Lat+"/Lng:"+Lng); // 클라이언트의 1명의 정보


                //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
                // 클라이언트 한 명 생성
                //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
                clientInfo = new ClientInfo(socket, email, name, Img, markerImg,"마커아직", Lat, Lng); // (소켓, 이메일, 유저이름, 합성마커)
                System.out.println("clientInfo 생성 직후 : "+clientInfo);

                //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
                // 새로 생성 or 기존 방 참여
                //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
                if (Map_rooms.get(roomNum) != null) { // 해당 key가 있다면

                    Map_rooms.get(roomNum).getArrayList().add(clientInfo); // ex.4번방의 클라이언트 명단에 이 사람을 추가한다.
                    서버접속자수++; //해당 방 말고 전체접속자 수 출력하기
                    System.out.println("^^서버접속자수 : "+서버접속자수);
                    System.out.println("Map_rooms : "+Map_rooms); // 입장할 때마다 조회하기
                    System.out.println("Map_rooms.size() : "+Map_rooms.size()); // 입장할 때마다 조회하기


                } else { // 해당 방번호가 없다면 hashMap에 추가한다. (서버DB는 안드에서 insert, 여기선 방 구분할 데이터를 가져와서 분리)

                    ArrayList<ClientInfo> arr_clientInfo = new ArrayList<>(); // 클라1명을 넣을 클라배열 생성
                    arr_clientInfo.add(clientInfo); // 클라배열에 클라1명 추가
                    서버접속자수++; //해당 방 말고 전체접속자 수 출력하기
                    Rooms room = new Rooms(roomName, arr_clientInfo); // (방이름, 클라들)
                    Map_rooms.put(roomNum, room); // 방 1개씩 담김 (방번호, 방정보)
                    System.out.println("roomNum... : "+roomNum);
                    System.out.println("roomName... : "+roomName);
                    System.out.println("Map_rooms에 방 추가... : "+Map_rooms);
                    System.out.println("Map_rooms에 방 추가... size() : "+Map_rooms.size());

                }

                // 입장 후 서버->클라
                sendMessage입장("입장",roomNum, email,name, Img, markerImg,"("+name+"님이 입장했습니다)", Lat, Lng); // 안내메세지 != 대화메세지


                //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
                // 소켓통신 ~ing
                // 한 클라이언트가 보낸 메시지를 다른 모든 클라이언트에게 보내준다.

                while (br != null) {

                    System.out.println("ServerReceiver while...");

                    try {

                        // 전송받은 메세지
                        String msg = br.readLine(); // 소켓에 꽂힌 스트림 통로로 받은 값

                        System.out.println(" 전송받은 메세지 msg : "+msg);
                        double 위치정보인가; // double로 변환할 수 있는지 없는지 판별 변수


                        // 위치인지 채팅메세지인지 판별
                        try {

                            위치정보인가 = Double.parseDouble(msg); // str to dou
                            System.out.println("위치정보인가 : "+위치정보인가);
                            목적 = "위치";

                        } catch (NumberFormatException e) { // 아예 에러나서 예외처리

                            // msg에 따라 '목적'에 String값이 다르게 들어감. 이걸로 구분함
                            if (msg.equals("강제종료")) {
                                목적 = "강제종료";
                            } else if (msg.equals("퇴장")) {
                                목적 = "퇴장";
                            } else if (msg.equals("운동시작")) { // after then.. 시간이 흘러감
                                목적 = "운동시작";
                            } else {
                                목적 = "채팅";
                            }

                        }


                        //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
                        // 위치 / 채팅 / 퇴장 / 강제종료 별 기능
                        //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
                        System.out.println("선별된 목적 : "+목적);
                        switch (목적) {

                            case "위치" :
                                try {
                                    System.out.println("dis가 String이 아니다.");
                                    String 위도 = msg; // 판별할 때 사용했던 변수 이름만 바꿈
                                    String 경도 = br.readLine(); // 소수점 7자리
                                    System.out.println("위도 : "+위도+" / 경도 : "+경도);

                                    // 위치 전송
                                    double Lat = Double.parseDouble(위도); // str to dou
                                    double Lng = Double.parseDouble(경도);

                                    System.out.println("sendMessage위치() 직전 Lat : "+Lat);
                                    System.out.println("sendMessage위치() 직전 clientInfo.getLat() : "+clientInfo.getLat());
                                    sendMessage위치(목적, roomNum, email, "none",getTime_now(), name, Lat, Lng);


                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                } break;

                            case "채팅" :
                                System.out.println("dis가 String이 맞다.");
                                System.out.println("["+socket.getInetAddress()+" : "+socket.getPort()+"]");
                                System.out.println("이름 : ["+name+"] msg... : "+msg);

                                // 메세지 전송
                                if (!msg.equals("")) sendMessage채팅(목적, roomNum, email, msg, name); // 대화 메세지를 해당방의 모두에게 전달한다.
                                break;


                            case "퇴장" :
                                System.out.println("퇴장 기능을 구현해야 한다.");
                                sendMessage퇴장(목적,roomNum,email,"("+name+"님이 나가셨습니다)",name, clientInfo);
                                break;


                            case "강제종료" :
                                System.out.println("강제종료 기능을 구현해야 한다.");
                                sendMessage강제종료(목적, roomNum, clientInfo);
                                break;


                            case "운동시작" :
                                System.out.println("운동시작 기능을 구현해야 한다.");
                                sendMessage운동시작(목적, roomNum);
                                break;


                        }


                    } catch (EOFException e) {
                        System.out.println("IOException 여기서 불린으로 반복문 멈출까?"); // 클라에서 소켓종료하면 여기로 옴
                        System.out.println("socket.isClosed() : "+socket.isClosed()); // 여기선 아직 소켓 안 닫힘
//                        dis.close(); // 수신 종료
                        br = null; // 반복문 조건 false 로 만들기
                    }

                } // ~while()
                //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ


            } catch (IOException e) {
                e.printStackTrace();

            } finally { //이 finally영역이 실행된다는 것은 클라이언트의 접속이 종료되었다는 의미이다.

//                try {
//
//
//                    System.out.println("finally roomNum... : "+roomNum);
//                    System.out.println("finally Map_rooms...1 : "+Map_rooms);
//
//                    int thisRoomCount = Map_rooms.get(roomNum).getArrayList().size(); // 이 방에 남은 인원이 0명이면 방이 삭제된다.
//                    System.out.println("finally thisRoomCount... : "+thisRoomCount);
//
//
//                    if (thisRoomCount == 0) { // 방에 있는 사람이 전부 나가게되면.. 방을 아예 삭제한다.
//                        Map_rooms.remove(roomNum);
//                        System.out.println("finally Map_rooms...2 : "+Map_rooms);
//                    }
//
//
//                    socket.close(); // 안드만 소켓닫으려했는데 여기도 닫아줘야 되는 것 같음. 스레드 끝나면 소켓은 알아서 없어지려나?
//                    서버접속자수--;
//
//                    System.out.println("["+socket.getInetAddress()+" : "+socket.getPort()+"] 에서 접속을 종료했습니다.");
//                    System.out.println("현재 서버 접속자 수는 "+서버접속자수+"명 입니다.");
//                    System.out.println("socket.isClosed() : "+socket.isClosed()); // 정상적으로 닫혔음을 확인
//
//                } catch (IOException e) {
//                    System.out.println("finally catch 예외발생");
//                    throw new RuntimeException(e);
//                }



            }
        }

    } // ~thread class


    /**
     * 입장 안내 메세지. Map에 저장된 전체 유저에게 클라정보를 전달한다.
     */
    public void sendMessage입장(String 목적, String 방번호, String email, String name, String Img, String imgURI, String msg, double Lat, double Lng) throws IOException {


        // 해당방의 입장한 클라이언트들의 소켓을 꺼내서 아웃풋스트림으로 메세지 전달하기 -> arr어떤방.get(0).getSocket()
        // 이걸 방에 입장한 사람 전부를 반복한다. -> arr어떤방
        System.out.println("sendMessage입장() email:"+email+" /name"+name+" /Img:"+Img+" /imgURI:"+imgURI+"msg : "+msg);
        ArrayList<ClientInfo> this방 = Map_rooms.get(방번호).getArrayList(); // 방이름으로 해당방을 찾음


        //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
        // 모든 참여자에게 메세지 전달
        //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
        for (int i=0; i< this방.size(); i++) { // 해당방에 참여한 한 명 한 명한테 6안내9메세지 전달

            String 방참여자수 = String.valueOf(this방.size());
            PrintWriter pw = new PrintWriter(this방.get(i).getSocket().getOutputStream());


            System.out.println("sendMessage 방참여자수 : "+방참여자수);
            System.out.println("sendMessage "+(i+1)+"번째 사람에게 전달");


//            JSONObject jsonObject = returnClientsInfo_입장외(목적, email, msg, Img, imgURI, "", name, Lat, Lng); // json으로 보낸다.
//            pw.println(jsonObject); // arr말고 object를 보냄. arr는 '입장'일 때만 보냄
//            pw.flush();


            // 입장할 때 모든 참여자 정보가 필요함.
            JSONArray jsonArray = returnClientsInfo_입장(this방, 목적, msg, "none", email); // 모든 정보를 넣어야되서 msg도 추가한다.


            pw.println(jsonArray); // 위에서 만든 참여자들 정보 객체
            pw.flush(); // 남김없이 전부 송신
            System.out.println("보냄 : "+(i+1)+"번째 사람에게");

        }

    }


    /**
     * 채팅 즉, Map에 저장된 전체 유저에게 대화 메세지를 전송하는 메시지
     */
    public void sendMessage채팅(String 목적, String 방번호, String email, String msg, String from) throws IOException {


        System.out.println("sendMessage(채팅) " +
                "\n목적 : "+목적+
                "\n방번호 : "+방번호+
                "\nemail : "+email+
                "\nmsg : "+msg+
                "\nfrom : "+from
        );

        ArrayList<ClientInfo> this방 = Map_rooms.get(방번호).getArrayList(); // 방이름으로 해당방을 찾음


        for (int i=0; i<this방.size(); i++) { // 해당 방에 참여한 한 명 한 명한테 6채팅9메세지 전달


            System.out.println("sendMessage(pram 3개) i : "+i);
            PrintWriter pw = new PrintWriter(this방.get(i).getSocket().getOutputStream());


            JSONObject jsonObject = returnClientsInfo_입장외(목적, email, msg, this방.get(i).getImg(), this방.get(i).getMarkerImg(),getTime_now(), from,  0, 0); // json으로 보낸다.


            pw.println(jsonObject); // 실제 보내는 함수
            pw.flush();

        }

    }


    /**
     * 위치 즉, 위도와 경도 값을 방에 입장한 모든 유저에게 전송하는 메서드 (마커를 찍는 데 사용된다.)
     */
    public void sendMessage위치 (String 목적, String 방번호, String email, String msg, String chatTime, String from, double Lat, double Lng) throws IOException {

        System.out.println("sendMessage(pram 2개) 방번호 : "+방번호);
        ArrayList<ClientInfo> this방 = Map_rooms.get(방번호).getArrayList(); // 방이름으로 해당방을 찾음


        for (int i=0; i< this방.size(); i++) { System.out.println("sendMessage i : "+i); // 해당방에 참여한 한 명 한 명한테 6안내9메세지 전달

            if (Map_rooms.get(방번호).getArrayList().get(i).getEmail().equals(email)) { // 이메일이 같으면 그 사람 위치 업뎃하라

                System.out.println("Map_rooms.get(방번호).getArrayList().get(i).getEmail() : "+email);
                System.out.println("전 Map_rooms.get(방번호).getArrayList().get(i).getLat() : "+Map_rooms.get(방번호).getArrayList().get(i).getLat());
                System.out.println("전 Map_rooms.get(방번호).getArrayList().get(i).getLat() : "+Map_rooms.get(방번호).getArrayList().get(i).getLng());
                Map_rooms.get(방번호).getArrayList().get(i).setLat(Lat); // 위치변경 업뎃해야 새로입장하는 사람도 최근 위치를 받아오지
                Map_rooms.get(방번호).getArrayList().get(i).setLng(Lng);
                System.out.println("후 Map_rooms.get(방번호).getArrayList().get(i).getLat() : "+Map_rooms.get(방번호).getArrayList().get(i).getLat()); // 변경되는 것 확인 함
                System.out.println("후 Map_rooms.get(방번호).getArrayList().get(i).getLng() : "+Map_rooms.get(방번호).getArrayList().get(i).getLng());

            }


            PrintWriter pw = new PrintWriter(this방.get(i).getSocket().getOutputStream());


            JSONObject jsonObject = returnClientsInfo_입장외(목적, email, msg,  this방.get(i).getImg(), this방.get(i).getMarkerImg(), chatTime, from, Lat, Lng); // json으로 보낸다.

            pw.println(jsonObject); // arr말고 object를 보냄. arr는 '입장'일 때만 보냄
            pw.flush();

        }

    }


    /**
     * 퇴장 메세지. Map에 저장된 전체 유저에게 안내 메세지를 전송하는 메시지
     */
    public void sendMessage퇴장(String 목적, String 방번호, String email, String msg, String from, ClientInfo clientInfo) throws IOException {


        System.out.println("sendMessage퇴장" +
                            "\n 목적 : "+목적+
                            "\n 목적 : "+msg
        );
        ArrayList<ClientInfo> this방 = Map_rooms.get(방번호).getArrayList(); // 방이름으로 해당방을 찾음


        System.out.println("해당 방의 참여자들 11... : "+Map_rooms.get(방번호).getArrayList());
        Map_rooms.get(방번호).getArrayList().remove(clientInfo); // 참여자는 삭제한다. 방에 지장 안 간다.
        System.out.println("해당 방의 참여자들 22... : "+Map_rooms.get(방번호).getArrayList());


        //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
        // 모든 참여자에게 메세지 전달
        //ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
        for (int i=0; i< this방.size(); i++) { // 해당방에 참여한 한 명 한 명한테 6안내9메세지 전달

            String 방참여자수 = String.valueOf(this방.size());
            PrintWriter pw = new PrintWriter(this방.get(i).getSocket().getOutputStream());


            System.out.println("sendMessage 방참여자수 : "+방참여자수);
            System.out.println("sendMessage "+(i+1)+"번째 사람에게 전달");


            // 퇴장할 때 모든 참여자 정보가 필요함.
            JSONArray jsonArray = returnClientsInfo_입장(this방, 목적, msg, "none", email); // 모든 정보를 넣어야되서 msg도 추가한다.


            pw.println(jsonArray); // 위에서 만든 참여자들 정보 객체
            pw.flush(); // 남김없이 전부 송신
            System.out.println("보냄 : "+(i+1)+"번째 사람에게");

        }

//
//        // 남은 전체에게 반복 메세지
//        for (int i=0; i<this방.size(); i++) { // 해당 방에 참여한 한 명 한 명한테 6채팅9메세지 전달
//
//            if (Map_rooms.get(방번호).getArrayList().get(i) != null) { // 방장이 null인 경우(퇴장한 경우)
//
//                System.out.println("sendMessage퇴장 i : "+i);
//
//                PrintWriter pw = new PrintWriter(this방.get(i).getSocket().getOutputStream());
//                JSONObject jsonObject = returnClientsInfo_입장외(목적, email, msg, "","시간",from,0,0); // json으로 보낸다.
//                System.out.println("해당 방의 참여자들 3... : "+jsonObject);
//
//                pw.println(jsonObject); // 4.퇴장 메세지
//                pw.flush();
//
//            }
//
//        } // ~for()
    }


    /**
     * 강제종료 메세지. 방장이 나간 후 남은 유저에게도 강제종료를 시키는 메세지를 보낸다.
     */
    public void sendMessage강제종료(String 목적, String 방번호, ClientInfo clientInfo) throws IOException {


        System.out.println("sendMessage퇴장 목적 : "+목적);
        ArrayList<ClientInfo> this방 = Map_rooms.get(방번호).getArrayList(); // 방이름으로 해당방을 찾음


        // 방장이 나간다면.. 방장을 제외한 사람들에게 메세지가 가고, 서버에서 방이 삭제된다.
        // 참여자가 나간다면.. 그 참여자를 제외한 사람들에게 메세지가 가고, 서버에서 그 사람이 삭제된다.

        for (int i=0; i<this방.size(); i++) { // 남아있는 사람들에게도 나가라고 안내하기. 그러면 다시 이 메소드로 참여자들이 올거임

            System.out.println("sendMessage퇴장-강제종료 i : "+i);

            PrintWriter pw = new PrintWriter(this방.get(i).getSocket().getOutputStream()); // 방장뺀 나머지에게
            JSONObject jsonObject = returnClientsInfo_입장외(목적, "", "", "", "", "","",0,0); // json으로 보낸다.
            System.out.println("해당 방의 참여자들 3... : "+jsonObject);

            pw.println(jsonObject); // "강제퇴장" keyword 전달
            pw.flush();

        }

        // error 주의 : 없는 사람한테 메세지 보내려고 했음
        System.out.println("해당 방의 참여자들 1... : "+Map_rooms.get(방번호).getArrayList());
        Map_rooms.get(방번호).getArrayList().remove(clientInfo); // 방장 너 삭제, 이 사실은 나머지 참여자에게도 전달해서 나가게할거임
        System.out.println("해당 방의 참여자들 2... : "+Map_rooms.get(방번호).getArrayList());
//        if (목적.equals("방종료")) {
//
//            // error 주의 : 없는 사람한테 메세지 보내려고 했음
//            System.out.println("해당 방 4... : "+Map_rooms);
//            Map_rooms.remove(방번호);
//            System.out.println("해당 방 5... : "+Map_rooms);
//
//        }
    }

    /**
     * 운동시작 메세지. 경과시간이 흘러가라는 메세지를 보낸다.
     */
    public void sendMessage운동시작(String 목적, String 방번호) throws IOException {


        System.out.println("sendMessage퇴장 목적 : " + 목적);
        ArrayList<ClientInfo> this방 = Map_rooms.get(방번호).getArrayList(); // 방이름으로 해당방을 찾음


        for (int i = 0; i < this방.size(); i++) { // 참여자들의 경과시간이 흘러가도록 하기


            System.out.println("sendMessage퇴장-운동시작 i : " + i);

            PrintWriter pw = new PrintWriter(this방.get(i).getSocket().getOutputStream()); // 방장뺀 나머지에게
            JSONObject jsonObject = returnClientsInfo_입장외(목적, "", "", "", "", "", "", 0, 0); // json으로 보낸다.
            System.out.println("해당 방의 참여자들 3... : " + jsonObject);

            pw.println(jsonObject); // "운동시작" keyword 전달
            pw.flush();

        }
    }


    /**
     * @return 현재시간을 12:33 처럼 시,분으로 리턴한다.
     */
    public String getTime_now() {

        // 현재 시간
        LocalTime now = LocalTime.now();

        // 포맷 정의하기
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        String Time_now_hhmm = now.format(formatter); // 12:33
        System.out.println("Time_now_hhmm : "+Time_now_hhmm);

        return Time_now_hhmm;
    }

    /**
     * @return 참여자들정보를 리턴한다.
     */
    public JSONArray returnClientsInfo_입장 (ArrayList<ClientInfo> this방, String 목적, String msg, String chatTime, String email){

        // arraylist -> json
        JSONArray jArray = new JSONArray();

        for (int j = 0; j < this방.size(); j++) {

            JSONObject sObject = new JSONObject(); // 배열 내에 들어갈 json
            sObject.put("purposes", 목적); // 입장/퇴장 중 1
            sObject.put("email", this방.get(j).getEmail());
            sObject.put("someOneEmail", email); // 입장/퇴장하는 사람의 이메일
            sObject.put("Img", this방.get(j).getImg());
            sObject.put("markerImg", this방.get(j).getMarkerImg());
            sObject.put("msg", msg);
            sObject.put("chatTime", chatTime);
            sObject.put("chatFrom", this방.get(j).getName());
            sObject.put("Lat", this방.get(j).getLat());
            sObject.put("Lng", this방.get(j).getLng());

            jArray.add(sObject); // 참여자들 정보가 들어있는 객체를 만들었다!

        }
        System.out.println("결과 jArray : "+jArray);

        return jArray;
    }

    /**
     * @return 방금 서버로 온 참여자 정보를 리턴한다.
     */
    public JSONObject returnClientsInfo_입장외 (String 목적, String email, String msg, String img, String markerImg, String chatTime, String chatFrom, double Lat, double Lng){

        // ClientInfo -> json

        JSONObject sObject = new JSONObject(); // 배열 내에 들어갈 json
        sObject.put("purposes", 목적); // 입장/채팅/위치/퇴장 중 1
        sObject.put("email", email);
        sObject.put("Img", img);
        sObject.put("markerImg", markerImg);
        sObject.put("msg", msg);
        sObject.put("chatTime", chatTime);
        sObject.put("chatFrom", chatFrom);
        sObject.put("Lat", Lat);
        sObject.put("Lng", Lng);


        System.out.println("결과 jArray : "+sObject);

        return sObject;
    }


}



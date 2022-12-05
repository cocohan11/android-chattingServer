import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class openAPI {

    public static void main(String[] args) {

        URL url = null;
        URLConnection urlConnection = null;

        String stringUrl = "https://open.assembly.go.kr/portal/openapi/ANUREPORT?KEY=395887b07ed7401f8683713c1ebf79a7&Type=json&pIndex=1&pSize=100";
        InputStream is = null;
        String data = "";

        try {

            url = new URL(stringUrl);
            urlConnection = url.openConnection();
            urlConnection.setDoOutput(true);

            is = urlConnection.getInputStream();

            byte[] buf = new byte[2048];
            int len = -1;
            StringBuffer sb = new StringBuffer();

            while ((len = is.read(buf, 0, buf.length)) != -1) {
                sb.append(new String(buf, 0, len));
            }

            data = sb.toString();
            System.out.println(data);

        } catch (MalformedURLException e) {
            e.getMessage();
        } catch (IOException e) {
            e.getMessage();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}

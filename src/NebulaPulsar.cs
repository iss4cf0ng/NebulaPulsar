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

    
}
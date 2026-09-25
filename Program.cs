using System;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Reflection;
using System.Text;
using System.Threading.Tasks;
using AndroidX.SwipeRefreshLayout.Widget;
using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.Graphics;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Runtime;
using Java.Interop;
using Android.Webkit;
using Android.Views;
using Android.Widget;
using ApplicationAttribute = Android.App.ApplicationAttribute;

namespace YulLanding;

[Activity(
    Label = "YUL Landing",
    Theme = "@android:style/Theme.Material.NoActionBar",
    MainLauncher = true,
    ConfigurationChanges = ConfigChanges.Orientation | ConfigChanges.ScreenSize | ConfigChanges.UiMode)]
public class MainActivity : Activity
{
    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);

        var stream = typeof(MainActivity).Assembly.GetManifestResourceStream("YulLanding.index.html")!;
        using var reader = new StreamReader(stream);
        var html = reader.ReadToEnd()
            .Replace("https://opensky-network.org/api/states/all", "/api/opensky/states/all")
            .Replace("https://aviationweather.gov/api/data/metar", "/api/metar")
            .Replace("https://api.open-meteo.com/v1/forecast", "/api/forecast");

        var server = new ProxyServer(html);

        var web = new WebView(this);
        web.Settings.JavaScriptEnabled = true;
        web.Settings.DomStorageEnabled = true;
        web.Settings.CacheMode = CacheModes.NoCache;

        var swipe = new SwipeRefreshLayout(this)
        {
            Background = new ColorDrawable(Color.ParseColor("#1B2844"))
        };
        swipe.SetColorSchemeColors(Color.ParseColor("#4FC3F7"));
        swipe.AddView(web, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.MatchParent));

        var pageClient = new PageClient();
        web.SetWebViewClient(pageClient);

        swipe.SetOnRefreshListener(new RefreshListener(() =>
        {
            web.ClearCache(true);
            web.LoadUrl(server.Url.ToString());
        }));
        pageClient.PageFinished += () =>
        {
            unsafe
            {
                fixed (JniArgumentValue* args = stackalloc JniArgumentValue[] { new JniArgumentValue(false) })
                    swipe.JniPeerMembers.InstanceMethods.InvokeVirtualVoidMethod("setRefreshing.(Z)V", swipe, args);
            }
        };

        SetContentView(swipe);
        web.LoadUrl(server.Url.ToString());
    }
}

class RefreshListener : Java.Lang.Object, SwipeRefreshLayout.IOnRefreshListener
{
    readonly Action Action;
    public RefreshListener(Action action) => Action = action;
    public void OnRefresh() => Action();
}

class PageClient : WebViewClient
{
    public Action? PageFinished;

    public override void OnPageFinished(WebView? view, string? url)
    {
        base.OnPageFinished(view, url);
        PageFinished?.Invoke();
    }
}

class ProxyServer
{
    static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(30) };
    string Html { get; }

    public Uri Url { get; }

    public ProxyServer(string html)
    {
        Html = html;
        var port = PickFreePort();
        var listener = new HttpListener();
        listener.Prefixes.Add($"http://127.0.0.1:{port}/");
        listener.Start();
        Url = new Uri($"http://127.0.0.1:{port}/");
        _ = Task.Run(async () =>
        {
            while (true)
            {
                var ctx = await listener.GetContextAsync();
                _ = Task.Run(() => Handle(ctx));
            }
        });
    }

    static int PickFreePort()
    {
        using var tcp = new TcpListener(IPAddress.Loopback, 0);
        tcp.Start();
        return ((IPEndPoint)tcp.LocalEndpoint).Port;
    }

    async void Handle(HttpListenerContext ctx)
    {
        try
        {
            var path = ctx.Request.Url?.AbsolutePath ?? "/";
            if (path == "/" || path == "/index.html")
            {
                var bytes = Encoding.UTF8.GetBytes(Html);
                ctx.Response.ContentType = "text/html; charset=utf-8";
                ctx.Response.ContentLength64 = bytes.Length;
                await ctx.Response.OutputStream.WriteAsync(bytes);
                return;
            }

            string upstream;
            if (path.StartsWith("/api/opensky/", StringComparison.Ordinal))
                upstream = "https://opensky-network.org/api" + path.Substring("/api/opensky".Length);
            else if (path.StartsWith("/api/metar", StringComparison.Ordinal))
                upstream = "https://aviationweather.gov/api/data" + path.Substring("/api".Length);
            else if (path.StartsWith("/api/forecast", StringComparison.Ordinal))
                upstream = "https://api.open-meteo.com/v1" + path.Substring("/api".Length);
            else
            {
                ctx.Response.StatusCode = 404;
                return;
            }
            upstream += ctx.Request.Url?.Query ?? "";

            using var up = await Http.GetAsync(upstream, HttpCompletionOption.ResponseHeadersRead);
            ctx.Response.StatusCode = (int)up.StatusCode;
            ctx.Response.AddHeader("Access-Control-Allow-Origin", "*");
            var ct = up.Content.Headers.ContentType?.MediaType;
            if (!string.IsNullOrEmpty(ct)) ctx.Response.ContentType = ct;
            var body = await up.Content.ReadAsByteArrayAsync();
            ctx.Response.ContentLength64 = body.Length;
            await ctx.Response.OutputStream.WriteAsync(body);
        }
        catch (Exception)
        {
            try { ctx.Response.StatusCode = 502; } catch { }
        }
        finally
        {
            ctx.Response.Close();
        }
    }
}

package com.doova.ktab.features.trailer.endcard;

import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the 1920x1080 end-card page. Every layer is the same page with only one element visible, so each
 * transparent PNG already sits at its final position and the agent only has to fade it in.
 * Relative file names the renderer must provide next to the page: fonts/Cairo.ttf, cover.jpg, logo.png.
 */
public final class EndCardHtml {

    public enum Layer {
        SCRIM("scrim.png"), COVER("cover.png"), TITLE("title.png"), AUTHOR("author.png"), LOGO("logo.png");

        private final String fileName;

        Layer(String fileName) {
            this.fileName = fileName;
        }

        public String fileName() {
            return fileName;
        }

        String cssName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private EndCardHtml() {
    }

    /** The layers to render, in z-order (bottom first). */
    public static List<Layer> layersFor(EndCardSpec spec) {
        List<Layer> layers = new ArrayList<>();
        layers.add(Layer.SCRIM);
        if (spec.hasCover()) {
            layers.add(Layer.COVER);
        }
        layers.add(Layer.TITLE);
        if (spec.hasAuthor()) {
            layers.add(Layer.AUTHOR);
        }
        layers.add(Layer.LOGO);
        return layers;
    }

    static int titleFontPx(String title) {
        int n = title == null ? 0 : title.strip().length();
        if (n <= 24) {
            return 72;
        }
        if (n <= 40) {
            return 60;
        }
        return n <= 56 ? 48 : 40;
    }

    public static String build(EndCardSpec spec, Layer only) {
        String title = HtmlUtils.htmlEscape(spec.title() == null ? "" : spec.title().strip());
        String author = HtmlUtils.htmlEscape(spec.hasAuthor() ? spec.author().strip() : "");
        int px = titleFontPx(spec.title());
        String stage = spec.hasCover() ? "stage" : "stage no-cover";
        StringBuilder card = new StringBuilder();
        if (spec.hasCover()) {
            card.append("<img class=\"cover\" src=\"cover.jpg\" alt=\"\">");
        }
        card.append("<div class=\"text\"><div class=\"title\" id=\"title\">").append(title).append("</div>");
        if (spec.hasAuthor()) {
            card.append("<div class=\"author\"><span class=\"rule\"></span>").append(author).append("</div>");
        }
        card.append("<img class=\"logo\" src=\"logo.png\" alt=\"\"></div>");
        return """
                <!DOCTYPE html>
                <html lang="ar" dir="rtl"><head><meta charset="utf-8"><style>
                @font-face{font-family:'Cairo';src:url('fonts/Cairo.ttf');font-weight:200 1000;}
                html,body{margin:0;padding:0;background:transparent;}
                .stage{position:relative;width:1920px;height:1080px;overflow:hidden;font-family:'Cairo',sans-serif;color:#fff;}
                .scrim{position:absolute;inset:0;
                  background:linear-gradient(to top,rgba(8,8,10,.55) 0%%,rgba(8,8,10,0) 28%%),
                  linear-gradient(to left,rgba(8,8,10,.8) 0%%,rgba(8,8,10,.8) 30%%,rgba(8,8,10,0) 75%%);}
                .card{position:absolute;top:0;right:180px;height:1080px;display:flex;align-items:center;gap:80px;}
                .no-cover .card{left:0;right:0;justify-content:center;text-align:center;}
                .cover{height:600px;width:auto;border-radius:8px;
                  box-shadow:0 40px 90px rgba(0,0,0,.55),0 0 0 1px rgba(255,255,255,.08);}
                .text{width:760px;display:flex;flex-direction:column;align-items:flex-start;gap:28px;}
                .no-cover .text{align-items:center;}
                .title{font-weight:700;font-size:%dpx;line-height:1.35;text-shadow:0 2px 12px rgba(0,0,0,.55);}
                .author{font-weight:400;font-size:34px;color:rgba(255,255,255,.78);display:flex;align-items:center;gap:20px;}
                .rule{display:inline-block;width:64px;height:2px;background:rgba(255,255,255,.78);}
                .logo{height:72px;max-width:420px;width:auto;object-fit:contain;margin-top:12px;}
                body:not(.only-scrim) .scrim,body:not(.only-cover) .cover,body:not(.only-title) .title,
                body:not(.only-author) .author,body:not(.only-logo) .logo{visibility:hidden;}
                </style></head>
                <body class="only-%s"><div class="%s"><div class="scrim"></div><div class="card">%s</div></div>
                <script>
                (function(){
                  window.__fitted=false;window.__overflow=false;
                  var t=document.getElementById('title');
                  function fit(){
                    var size=parseInt(getComputedStyle(t).fontSize,10);
                    while(t.getBoundingClientRect().height>3*size*1.35+2&&size>32){size-=2;t.style.fontSize=size+'px';}
                    window.__overflow=t.getBoundingClientRect().height>3*size*1.35+2;
                    window.__fitted=true;
                  }
                  document.fonts.load('700 40px Cairo').then(function(){return document.fonts.ready;}).then(fit,fit);
                })();
                </script></body></html>
                """.formatted(px, only.cssName(), stage, card);
    }
}

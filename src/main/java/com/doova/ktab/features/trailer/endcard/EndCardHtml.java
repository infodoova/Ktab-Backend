package com.doova.ktab.features.trailer.endcard;

import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the 1920x1080 end-card page. Every layer is the same page with only one element visible, so each
 * transparent PNG already sits at its final position and the agent only has to fade it in.
 * Layout: cover on the left; centred title, subtitle, rule and author on the right; logo at the bottom centre.
 * The rule is its own layer so the agent can animate it drawing outwards from its centre.
 * Relative file names the renderer must provide next to the page: fonts/Cairo.ttf, cover.jpg, logo.png.
 */
public final class EndCardHtml {

    public enum Layer {
        SCRIM("scrim.png"), COVER("cover.png"), TITLE("title.png"), SUBTITLE("subtitle.png"),
        RULE("rule.png"), AUTHOR("author.png"), LOGO("logo.png");

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
        if (!subtitleOf(spec.title()).isEmpty()) {
            layers.add(Layer.SUBTITLE);
        }
        if (spec.hasAuthor()) {
            layers.add(Layer.RULE);
            layers.add(Layer.AUTHOR);
        }
        layers.add(Layer.LOGO);
        return layers;
    }

    /** Ktab stores one title string; a "main: subtitle" title is shown as two lines. */
    static String mainTitleOf(String title) {
        String t = title == null ? "" : title.strip();
        int colon = t.indexOf(':');
        return colon > 0 && !t.substring(colon + 1).isBlank() ? t.substring(0, colon).strip() : t;
    }

    static String subtitleOf(String title) {
        String t = title == null ? "" : title.strip();
        int colon = t.indexOf(':');
        return colon > 0 ? t.substring(colon + 1).strip() : "";
    }

    static int titleFontPx(String mainTitle) {
        int n = mainTitle == null ? 0 : mainTitle.strip().length();
        if (n <= 24) {
            return 60;
        }
        if (n <= 40) {
            return 52;
        }
        return n <= 56 ? 44 : 38;
    }

    public static String build(EndCardSpec spec, Layer only) {
        String main = mainTitleOf(spec.title());
        String subtitle = subtitleOf(spec.title());
        int px = titleFontPx(main);
        String stage = spec.hasCover() ? "stage" : "stage no-cover";
        StringBuilder text = new StringBuilder("<div class=\"text\"><div class=\"title\" id=\"title\">")
                .append(HtmlUtils.htmlEscape(main)).append("</div>");
        if (!subtitle.isEmpty()) {
            text.append("<div class=\"subtitle\">").append(HtmlUtils.htmlEscape(subtitle)).append("</div>");
        }
        if (spec.hasAuthor()) {
            text.append("<div class=\"rule\"></div><div class=\"author\">")
                    .append(HtmlUtils.htmlEscape(spec.author().strip())).append("</div>");
        }
        text.append("</div>");
        String cover = spec.hasCover() ? "<img class=\"cover\" src=\"cover.jpg\" alt=\"\">" : "";
        return """
                <!DOCTYPE html>
                <html lang="ar" dir="rtl"><head><meta charset="utf-8"><style>
                @font-face{font-family:'Cairo';src:url('fonts/Cairo.ttf');font-weight:200 1000;}
                html,body{margin:0;padding:0;background:transparent;}
                .stage{position:relative;width:1920px;height:1080px;overflow:hidden;font-family:'Cairo',sans-serif;color:#fff;}
                .scrim{position:absolute;inset:0;
                  background:radial-gradient(ellipse at 55%% 50%%,rgba(6,6,8,.30) 0%%,rgba(6,6,8,.62) 100%%),
                  linear-gradient(to top,rgba(6,6,8,.55) 0%%,rgba(6,6,8,0) 30%%);}
                .cover{position:absolute;left:240px;top:250px;height:580px;width:auto;border-radius:6px;
                  box-shadow:0 40px 90px rgba(0,0,0,.55),0 0 0 1px rgba(255,255,255,.08);}
                .text{position:absolute;left:930px;width:800px;top:0;height:1080px;display:flex;flex-direction:column;
                  align-items:center;justify-content:center;gap:30px;text-align:center;}
                .no-cover .text{left:560px;}
                .title{font-weight:700;font-size:%dpx;line-height:1.35;text-shadow:0 2px 12px rgba(0,0,0,.6);}
                .subtitle{font-weight:500;font-size:34px;line-height:1.4;color:rgba(255,255,255,.9);text-shadow:0 2px 10px rgba(0,0,0,.6);}
                .author{font-weight:400;font-size:30px;color:rgba(255,255,255,.82);text-shadow:0 2px 10px rgba(0,0,0,.6);}
                .rule{width:350px;height:2px;background:rgba(255,255,255,.85);}
                .logo{position:absolute;left:50%%;bottom:96px;transform:translateX(-50%%);height:56px;width:auto;max-width:360px;object-fit:contain;}
                body:not(.only-scrim) .scrim,body:not(.only-cover) .cover,body:not(.only-title) .title,
                body:not(.only-subtitle) .subtitle,body:not(.only-rule) .rule,body:not(.only-author) .author,body:not(.only-logo) .logo{visibility:hidden;}
                </style></head>
                <body class="only-%s"><div class="%s"><div class="scrim"></div>%s%s<img class="logo" src="logo.png" alt=""></div>
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
                """.formatted(px, only.cssName(), stage, cover, text);
    }
}

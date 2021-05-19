let url = "";
let button = $("#button");

$((function () {
    const queryString = window.location.search;
    const urlParams = new URLSearchParams(queryString);

    $.ajax({
        dataType: "json",
        url: "localhost:64700/api/validatecode",
        data: "{\"code\":\"" + urlParams.get("code") + "\"}",
        error: function() {
            button.html("Erreur de communication avec le serveur !");
            button.css("background", "var(--red)");
        },
        success: function (data) {
            if (data["result"] === "ok") {
                button.html("Se connecter");
                button.css("background", "var(--green)");

                url = data["url"];
            } else {
                button.html("Code invalide");
                button.css("background", "var(--red)");
            }
        }
    });
}));

function connect() {
    if (url === "") {
        return;
    }

    window.location.href = url;
}
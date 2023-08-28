<#macro header>
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <title>Health Insurance North</title>
        <style>
             table.center {
                margin-left: auto;
                margin-right: auto;
            }
            td, th {
                padding: 0 10px ;
                text-align: left;
            }
        </style>
    </head>
    <body style="text-align: center; font-family: sans-serif">
    <img src="/static/Krankenkasse_Blau_gematik.svg" alt="insurance company" width="100">
    <h1>Health Insurance North</h1>
    <hr>
    <#nested>
    <hr>
    <a href="/insurance">Back to the main page</a>
    </body>
    </html>
</#macro>
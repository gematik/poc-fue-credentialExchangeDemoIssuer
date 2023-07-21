<#import "_layout_medicaloffice.ftl" as layout />
<@layout.header>
    <div style="text-align: center;">
        <h3>
            Welcome ${invitation.givenName} ${invitation.name}.
        </h3>
        <p>Click or scan qr-code to request your vaccination credential from ${invitation.dateOfVaccination?date}.</p>
            <a href="${invitation.url}">
                <img style='width:256px; height:256px;' id='base64image' alt="qr-code-invite"
                     src='data:image/jpeg;base64, ${invitation.qrCode}' />
            </a>
    </div>
</@layout.header>
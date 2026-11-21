param(
    [Parameter(Mandatory)] [string] $BaseApk,
    [Parameter(Mandatory)] [string] $ApktoolJar,
    [string] $JavaDir = $env:JAVA_HOME,
    [string] $AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string] $OutputApk = "$PSScriptRoot/../dist/QP-Gallery-10.0.2-Comic-Standalone-v8.apk"
)

$ErrorActionPreference = 'Stop'

function Invoke-Checked([string] $Program, [string[]] $Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Program exited with code $LASTEXITCODE"
    }
}

$BaseApk = (Resolve-Path -LiteralPath $BaseApk).Path
$ApktoolJar = (Resolve-Path -LiteralPath $ApktoolJar).Path
$OutputApk = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputApk)
$ExpectedHash = 'ABFB989235572CA1096913434CB809E34895CB88B35A637D2A1B7D01D0E8DC65'
if ((Get-FileHash -LiteralPath $BaseApk -Algorithm SHA256).Hash -ne $ExpectedHash) {
    throw "Use the original 10.0.2 Alpha 2 alpha.apk from WSTxda/QP-Gallery-Releases."
}
if (!$JavaDir) {
    throw 'Set JAVA_HOME or pass the JDK root using -JavaDir.'
}
$env:JAVA_HOME = $JavaDir
$Java = Join-Path $JavaDir 'bin/java.exe'
$Javac = Join-Path $JavaDir 'bin/javac.exe'
$Keytool = Join-Path $JavaDir 'bin/keytool.exe'
$BuildTools = Join-Path $AndroidSdk 'build-tools/36.1.0'
$Platform = Join-Path $AndroidSdk 'platforms/android-36/android.jar'
$Patcher = Join-Path $PSScriptRoot 'patch_apk.py'
$Work = Join-Path ([IO.Path]::GetTempPath()) ("qp-comic-build-" + [guid]::NewGuid())
$Decoded = Join-Path $Work 'decoded'
$Rebuilt = Join-Path $Work 'rebuilt.apk'
$Unsigned = Join-Path $Work 'unsigned.apk'
$Aligned = Join-Path $Work 'aligned.apk'
$Classes = Join-Path $Work 'classes'
$Stubs = Join-Path $Work 'stubs'
$Keys = Join-Path $env:LOCALAPPDATA 'QP-Gallery-Comic'
$KeyStore = Join-Path $Keys 'comic-test.p12'
New-Item -ItemType Directory -Force -Path $Work,$Classes,"$Stubs/com/alensw/a","$Stubs/com/alensw/bean","$Stubs/com/alensw/ui/c",$Keys,(Split-Path -Parent $OutputApk) | Out-Null

try {
    # Compile against the upstream signatures. Only the comic classes go into the new DEX.
    @'
package com.alensw.a;
public class e extends java.io.File {
    public int c;
    public java.util.ArrayList<e> h;
    public e(java.io.File file, int order) {
        super(file.getPath());
    }
    public native void a(char type, int time, long size, String name);
    public native int j();
    public native char f(int index);
    public native android.net.Uri d(int index);
}
'@ | Set-Content -Encoding ascii "$Stubs/com/alensw/a/e.java"
    @'
package com.alensw.bean;
public class CommonFile {
    public native String a();
    public native char h();
    public native android.net.Uri i();
}
'@ | Set-Content -Encoding ascii "$Stubs/com/alensw/bean/CommonFile.java"
    @'
package com.alensw.bean;
public class CommonFolder extends CommonFile {
    public int k;
    public native int o();
    public native CommonFile a(int index);
}
'@ | Set-Content -Encoding ascii "$Stubs/com/alensw/bean/CommonFolder.java"
    @'
package com.alensw.ui.c;
public class cx extends android.app.Activity {
    public native void a(boolean visible);
    public native void a(android.view.ViewGroup bottomBar);
}
'@ | Set-Content -Encoding ascii "$Stubs/com/alensw/ui/c/cx.java"

    $Sources = @(Get-ChildItem -Path "$PSScriptRoot/src",$Stubs -Filter '*.java' -Recurse |
        Select-Object -ExpandProperty FullName)
    Invoke-Checked $Javac (@('-encoding', 'UTF-8', '-source', '8', '-target', '8',
        '-classpath', $Platform, '-d', $Classes) + $Sources)
    $ComicClasses = @(Get-ChildItem "$Classes/com/alensw/comic" -Filter '*.class' |
        Select-Object -ExpandProperty FullName)
    Invoke-Checked "$BuildTools/d8.bat" (@('--min-api', '26', '--lib', $Platform,
        '--classpath', $Classes, '--output', $Work) + $ComicClasses)

    Invoke-Checked $Java @('-jar', $ApktoolJar, 'd', '--no-res', '-o', $Decoded, $BaseApk)
    Invoke-Checked 'py' @('-3', '-B', $Patcher, 'patch', $Decoded)
    Invoke-Checked $Java @('-jar', $ApktoolJar, 'b', '-o', $Rebuilt, $Decoded)
    Invoke-Checked 'py' @('-3', '-B', $Patcher, 'package', $BaseApk, $Rebuilt, "$Work/classes.dex", $Unsigned)
    Invoke-Checked "$BuildTools/zipalign.exe" @('-P', '16', '-f', '4', $Unsigned, $Aligned)

    if (!(Test-Path -LiteralPath $KeyStore)) {
        Invoke-Checked $Keytool @('-genkeypair', '-keystore', $KeyStore, '-storetype', 'PKCS12',
            '-storepass', 'android', '-alias', 'qp-comic-test', '-dname', 'CN=QP Gallery Comic Test',
            '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000')
    }
    Invoke-Checked "$BuildTools/apksigner.bat" @('sign', '--v4-signing-enabled', 'false', '--ks', $KeyStore,
        '--ks-pass', 'pass:android', '--out', $OutputApk, $Aligned)
    Invoke-Checked "$BuildTools/apksigner.bat" @('verify', '--verbose', $OutputApk)
    Write-Output $OutputApk
} finally {
    $TempPath = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $ResolvedWork = (Resolve-Path -LiteralPath $Work).Path
    if ($ResolvedWork.StartsWith($TempPath, [StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path -Leaf $ResolvedWork) -like 'qp-comic-build-*') {
        Remove-Item -LiteralPath $ResolvedWork -Recurse -Force
    }
}

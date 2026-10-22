param(
    [Parameter(Mandatory)][string]$CaptureFolder,
    [Parameter(Mandatory)][string]$CaptureLog,
    [Parameter(Mandatory)][string]$OutputFolder,
    [switch]$AllowPartial
)
$ErrorActionPreference='Stop'
$source=(Resolve-Path -LiteralPath $CaptureFolder).Path
$log=(Resolve-Path -LiteralPath $CaptureLog).Path
New-Item -ItemType Directory -Path $OutputFolder -Force | Out-Null
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing.Common,System.Drawing.Primitives,System.Private.Windows.GdiPlus,System.Private.Windows.Core -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
public static class StormAlphaCompare {
    private static byte[] Read(string file, out int width, out int height) {
        using(var bitmap=new Bitmap(file)) {
            width=bitmap.Width;height=bitmap.Height;
            var data=bitmap.LockBits(new Rectangle(0,0,width,height),ImageLockMode.ReadOnly,PixelFormat.Format32bppArgb);
            try {
                byte[] values=new byte[width*height*4];
                for(int y=0;y<height;y++)Marshal.Copy(IntPtr.Add(data.Scan0,y*data.Stride),values,y*width*4,width*4);
                return values;
            } finally {bitmap.UnlockBits(data);}
        }
    }
    public static double[] Compare(string fileA,string fileB) {
        int aw,ah,bw,bh;var a=Read(fileA,out aw,out ah);var b=Read(fileB,out bw,out bh);
        if(aw!=bw||ah!=bh)throw new Exception("Mismatched native target dimensions");
        long ca=0,cb=0,added=0,removed=0,changed=0,total=0;int max=0;
        for(int i=3;i<a.Length;i+=4) {
            bool aa=a[i]>2,bb=b[i]>2;if(aa)ca++;if(bb)cb++;
            if(aa&&!bb)added++;if(bb&&!aa)removed++;
            int delta=Math.Abs(a[i]-b[i]);if(delta!=0)changed++;total+=delta;max=Math.Max(max,delta);
        }
        return new double[]{aw,ah,ca,cb,added,removed,changed,total/(255.0*aw*ah),max/255.0};
    }
}
'@
$growth=@{};$poses=@{};$flags=@{}
foreach($line in Get-Content -LiteralPath $log) {
    if($line -match 'T256_GROWTH shot=(\S+) growthSignature=(\S+).*envelopeContribution=(true|false)') {
        $growth[$Matches[1]]=$Matches[2];$flags[$Matches[1]]=$Matches[3]
    }
    if($line -match 'T256_POSE shot=(\S+).*camera=([^ ]+) aim=([^ ]+)') {
        $poses[$Matches[1]]="$($Matches[2])/$($Matches[3])"
    }
}
$records=@();$missing=@()
foreach($type in @('cumulonimbus_calvus','cumulonimbus_capillatus','supercell')) {
    foreach($view in @('side','above','close')) {
        $base="T256_${type}__20261008__"
        $a=$base+"envelope_A_$view";$b=$base+"envelope_B_$view";$legacy=$base+"baseline_T255_$view"
        foreach($pair in @(@($a,$b,'envelope_A_vs_B'),@($legacy,$b,'T255_vs_growth_B'))) {
            $left=$pair[0];$right=$pair[1]
            $leftFile=Join-Path $source ($left+'__raw.png');$rightFile=Join-Path $source ($right+'__raw.png')
            if(!(Test-Path -LiteralPath $leftFile) -or !(Test-Path -LiteralPath $rightFile)) {
                if($AllowPartial) {$missing+="$left / $right";continue}
                throw "Missing pair: $left / $right"
            }
            if(!$poses[$left] -or $poses[$left] -ne $poses[$right]) {throw "Mismatched native framing: $left / $right"}
            if($pair[2] -eq 'envelope_A_vs_B' -and (!$growth[$left] -or $growth[$left] -ne $growth[$right] -or $flags[$left] -ne 'true' -or $flags[$right] -ne 'false')) {throw 'Uncontrolled envelope recipe pair'}
            $r=[StormAlphaCompare]::Compare($leftFile,$rightFile)
            $records += [ordered]@{type=$type;view=$view;comparison=$pair[2];left=$left;right=$right;framing=$poses[$left];growth_signature=$growth[$right];
                width=$r[0];height=$r[1];left_occupied=$r[2];right_occupied=$r[3];left_only=$r[4];right_only=$r[5];changed_alpha_pixels=$r[6];mean_absolute_alpha=$r[7];max_absolute_alpha=$r[8];
                left_sha256=(Get-FileHash -LiteralPath $leftFile).Hash;right_sha256=(Get-FileHash -LiteralPath $rightFile).Hash}
        }
    }
}
$result=[ordered]@{source_log=$log;capture_folder=$source;missing=$missing;status='DIAGNOSTIC_NOT_ANATOMY_ACCEPTANCE';
    method='Read-only native PNG alpha comparison; opacity threshold 2/255. Byte quantization and ray-entry lattice differences from loose bounds are not exact density-field attribution. CPU unbounded A/B isolates envelope support.';records=$records}
[System.IO.File]::WriteAllText((Join-Path $OutputFolder 'comparisons.json'),($result|ConvertTo-Json -Depth 8))
Write-Output "Validated identical framing and geometry signatures for $($records.Count) native comparisons; NOT a screenshot acceptance test."

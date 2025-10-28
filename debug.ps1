Copy-Item -Path "build/libs/${args}.jar" -Destination "D:\MCServer\paper-1.20.1\plugins" -Recurse -Force
start -WorkingDirectory "D:\MCServer\paper-1.20.1" powershell ./starter.bat

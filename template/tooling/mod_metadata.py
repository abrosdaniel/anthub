"""Static checks of top-level NeoForge JAR metadata; never executes mod code."""
import io
import re
import tomllib
import zipfile

def numeric(version):
    if not re.fullmatch(r'\d+(?:\.\d+)*',version):return None
    parts=list(map(int,version.split('.')))
    while len(parts)>1 and parts[-1]==0:parts.pop()
    return tuple(parts)

def accepts(expression,version):
    """Numeric Maven intervals/unions. None means unsupported rather than compatible."""
    target=numeric(version)
    if target is None:return None
    expression=re.sub(r'\s+', '', expression)
    if not expression:return True
    if not expression.startswith(('[','(')):return True if numeric(expression) else None # Maven soft recommendation
    intervals=re.findall(r'[\[(][^\[\]()]*[\])]',expression)
    if not intervals or ','.join(intervals)!=expression:return None
    for interval in intervals:
        body=interval[1:-1]
        if ',' not in body:
            value=numeric(body)
            if value is None or interval[0]!='[' or interval[-1]!=']':return None
            if target==value:return True
            continue
        low,high=body.split(',',1);a=numeric(low) if low else None;b=numeric(high) if high else None
        if (low and a is None) or (high and b is None):return None
        if (a is None or target>a or (target==a and interval[0]=='[')) and (b is None or target<b or (target==b and interval[-1]==']')):return True
    return False

def accepts_dependency(expression, identity, version, minecraft):
    """Match FML's 1.21.1 VersionSupportMatrix, including its fallback versions.

    Source: NeoForged/FancyModLoader, branch 1.21.1,
    loader/src/main/java/net/neoforged/fml/loading/VersionSupportMatrix.java
    """
    result=accepts(expression,version)
    if result is True:return True
    fallback={'minecraft':'1.21','neoforge':'21.0.166'}.get(identity) if minecraft=='1.21.1' else None
    if fallback is None:return result
    alternate=accepts(expression,fallback)
    if alternate is True:return True
    return None if result is None or alternate is None else False

def inspect_jar(data,path,minecraft,neoforge,seen):
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            name='META-INF/neoforge.mods.toml'
            if name not in jar.namelist():
                if 'fabric.mod.json' in jar.namelist() or 'quilt.mod.json' in jar.namelist() or 'META-INF/mods.toml' in jar.namelist():
                    raise ValueError('Wrong loader: '+path+' has no NeoForge metadata')
                print('Warning: cannot verify mod metadata (library/custom loader): '+path);return
            entry=jar.getinfo(name)
            if entry.file_size>1024*1024:raise ValueError('Mod metadata too large: '+path)
            metadata=tomllib.loads(jar.read(entry).decode('utf-8'))
    except (zipfile.BadZipFile,tomllib.TOMLDecodeError,UnicodeDecodeError) as error:
        raise ValueError('Invalid JAR metadata: '+path) from error
    mods=metadata.get('mods',[])
    if not mods:raise ValueError('Missing modId: '+path)
    for mod in mods:
        identity=mod.get('modId','')
        if not re.fullmatch(r'[a-z][a-z0-9_]{1,63}',identity):raise ValueError('Invalid modId in '+path)
        if identity in seen:raise ValueError('Duplicate modId '+identity+': '+seen[identity]+' and '+path)
        seen[identity]=path
    for dependencies in metadata.get('dependencies',{}).values():
        for dependency in dependencies:
            if dependency.get('side','BOTH')=='SERVER' or dependency.get('type','required') in ('incompatible','discouraged'):continue
            identity=dependency.get('modId');version={'minecraft':minecraft,'neoforge':neoforge}.get(identity)
            if version is None:continue
            requirement=dependency.get('versionRange','');result=accepts_dependency(requirement,identity,version,minecraft)
            if result is False:raise ValueError(f'{path} requires {identity} {requirement}; project uses {version}. Bounds with () are excluded; bounds with [] are included. Choose a compatible mod file: the JAR metadata, not its filename, defines compatibility.')
            if result is None:print(f'Warning: cannot evaluate {identity} range {requirement} in {path}')

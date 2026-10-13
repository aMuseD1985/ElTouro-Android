#!/usr/bin/env python3
"""Writes a stand-in R.java (string/style/xml/mipmap ids) from the app's resources, for the local type check only."""
import glob, os, re, sys
repo, out = sys.argv[1], sys.argv[2]
res = os.path.join(repo, 'android/app/src/main/res')
strings = set()
for f in glob.glob(res + '/values*/*.xml'):
    strings |= set(re.findall(r'<string name="([^"]+)"', open(f, encoding='utf-8').read()))
styles = {n.replace('.', '_') for f in glob.glob(res + '/values/*.xml') for n in re.findall(r'<style name="([^"]+)"', open(f).read())}
xmls = {os.path.splitext(os.path.basename(f))[0] for f in glob.glob(res + '/xml/*.xml')}
mipmaps = {os.path.splitext(os.path.basename(f))[0] for f in glob.glob(res + '/mipmap-*/*')}
src = 'package de.eltouro.app;\npublic final class R {\n'
for cls, names in [('string', strings), ('style', styles), ('xml', xmls), ('mipmap', mipmaps)]:
    src += '  public static final class %s {\n' % cls + ''.join('    public static final int %s = %d;\n' % (n, i + 1) for i, n in enumerate(sorted(names))) + '  }\n'
os.makedirs(os.path.join(out, 'de/eltouro/app'), exist_ok=True)
open(os.path.join(out, 'de/eltouro/app/R.java'), 'w').write(src + '}\n')

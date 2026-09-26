# T232. Compile a lean FINAL fragment shader on the NVIDIA driver and dump the
# program binary's embedded assembly stats (register count, instruction count).
import sys, re, os
import moderngl
from OpenGL import GL

ROOT = r'C:\Users\matga\Downloads\Project-Atmosphere'
INC = os.path.join(ROOT, r'src\main\resources\assets\projectatmosphere\shaders\include')


def resolve(src):
    def imp(m):
        name = m.group(1).split(':')[1]
        return open(os.path.join(INC, name), encoding='utf-8').read()
    return re.sub(r'#moj_import <([^>]+)>', imp, src)


VS = """#version 150
in vec3 Position;
out vec2 texCoord;
void main(){ texCoord = Position.xy; gl_Position = vec4(Position, 1.0); }
"""


def main(path, out):
    ctx = moderngl.create_standalone_context(require=430)
    fs = resolve(open(path, encoding='utf-8').read())
    prog = ctx.program(vertex_shader=VS, fragment_shader=fs)
    pid = prog.glo
    n = GL.glGetProgramiv(pid, GL.GL_PROGRAM_BINARY_LENGTH)
    length = GL.GLsizei(0)
    fmt = GL.GLenum(0)
    buf = (GL.GLubyte * int(n))()
    GL.glGetProgramBinary(pid, n, length, fmt, buf)
    data = bytes(buf)[:length.value]
    open(out, 'wb').write(data)
    txt = data.decode('latin-1')
    print(path.split('\\')[-1], 'binary', len(data))
    for key in ('!!NV', 'REGISTERS', 'registers', 'TEMP', 'regcount', 'numRegs'):
        for m in re.finditer(re.escape(key) + r'[^\n\x00]{0,80}', txt):
            print('  ', m.group(0)[:100])
            break


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])

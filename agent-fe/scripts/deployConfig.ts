import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

type YamlValue = string | Record<string, YamlValue>;

/**
 * 极简 YAML 解析：仅支持「嵌套 map + 标量字符串」，满足 deploy/config.yml。
 */
function parseSimpleYaml(text: string): Record<string, YamlValue> {
  const root: Record<string, YamlValue> = {};
  const stack: { indent: number; obj: Record<string, YamlValue> }[] = [{ indent: -1, obj: root }];

  for (const rawLine of text.split(/\r?\n/)) {
    if (!rawLine.trim() || rawLine.trim().startsWith('#')) {
      continue;
    }
    const indent = rawLine.search(/\S/);
    const line = rawLine.trim();
    const match = line.match(/^([A-Za-z0-9_]+):\s*(.*)$/);
    if (!match) {
      continue;
    }
    const [, key, rawValue] = match;
    while (stack.length > 1 && indent <= stack[stack.length - 1].indent) {
      stack.pop();
    }
    const current = stack[stack.length - 1].obj;
    if (rawValue === '') {
      const child: Record<string, YamlValue> = {};
      current[key] = child;
      stack.push({ indent, obj: child });
    } else {
      current[key] = expandEnv(rawValue.replace(/^["']|["']$/g, ''));
    }
  }
  return root;
}

function expandEnv(value: string): string {
  return value.replace(/\$\{([A-Za-z0-9_]+)(?::([^}]*))?\}/g, (_m, name: string, def?: string) => {
    const fromEnv = process.env[name];
    if (fromEnv !== undefined && fromEnv !== '') {
      return fromEnv;
    }
    return def ?? '';
  });
}

const configPath = path.resolve(__dirname, 'deploy/config.yml');
const deployConfig = parseSimpleYaml(fs.readFileSync(configPath, 'utf8')) as {
  OSS_CONFIG: Record<string, string>;
  S3_CONFIG: Record<string, string>;
};

export { deployConfig };

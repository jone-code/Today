#!/usr/bin/env node
// Validate deployment configuration without printing secrets or changing external state.
import { Buffer } from 'node:buffer';
const failures = [];
const required = name => {
  const value = process.env[name]?.trim();
  if (!value) failures.push(`${name} is required`);
  return value || '';
};
const emails = required('TODAY_INTERNAL_EMAIL_ALLOWLIST');
if (emails && emails.split(',').some(email => !/^[^\s@*,]+@[^\s@*,]+\.[^\s@*,]+$/.test(email.trim()))) failures.push('Internal allowlist must contain exact email addresses');
const jwt = required('TODAY_JWT_SECRET');
if (jwt && (jwt.length < 32 || jwt.includes('today-dev-jwt-secret'))) failures.push('JWT secret must be an independent secret of at least 32 characters');
const metrics = required('TODAY_METRICS_ADMIN_TOKEN');
if (metrics && (metrics.length < 32 || metrics === jwt)) failures.push('Metrics token must be an independent secret of at least 32 characters');
const key = required('TODAY_DATA_EXPORT_KEY');
if (key && (Buffer.from(key, 'base64').length !== 32 || Buffer.from(key, 'base64').toString('base64') !== key)) failures.push('Export key must be a base64-encoded 32-byte key');
if (key && (jwt === key || metrics === key)) failures.push('JWT and metrics secrets must be independent from the export key');
const url = required('TODAY_PUBLIC_API_URL');
try { if (url && new URL(url).protocol !== 'https:') failures.push('Public API must use HTTPS'); }
catch { failures.push('Public API URL is invalid'); }
const positiveInteger = name => {
  const raw = process.env[name]?.trim();
  if (!raw) return null;
  if (!/^\d+$/.test(raw) || Number(raw) < 1) failures.push(`${name} must be a positive integer`);
  return Number(raw);
};
const voiceHour = positiveInteger('TODAY_VOICE_PER_HOUR');
const voiceDay = positiveInteger('TODAY_VOICE_PER_DAY');
if (voiceHour !== null && voiceDay !== null && voiceHour > voiceDay) failures.push('TODAY_VOICE_PER_HOUR must not exceed TODAY_VOICE_PER_DAY');
const auditRetainDays = positiveInteger('TODAY_METRICS_AUDIT_RETAIN_DAYS');
if (auditRetainDays !== null && auditRetainDays > 3650) failures.push('TODAY_METRICS_AUDIT_RETAIN_DAYS must not exceed 3650 days');
const booleanFlag = name => {
  const raw = process.env[name]?.trim();
  if (raw && raw !== 'true' && raw !== 'false') failures.push(`${name} must be true or false`);
};
booleanFlag('TODAY_DATA_EXPORT_ENABLED');
booleanFlag('TODAY_ACCOUNT_DELETE_ENABLED');
if (failures.length) {
  for (const failure of failures) console.error(failure);
  process.exitCode = 1;
} else console.log('Internal release configuration passed. Device, backup, monitoring and rollback evidence must be checked separately.');

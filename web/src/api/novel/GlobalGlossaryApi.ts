import type { GlobalGlossary } from '@/model/GlobalGlossary';
import { client } from './client';

const listGlobalGlossaries = () =>
  client.get('global-glossary').json<GlobalGlossary[]>();

const getGlobalGlossary = (id: string) =>
  client.get(`global-glossary/${id}`).json<GlobalGlossary>();

const createGlobalGlossary = (json: {
  name: string;
  content: { [key: string]: string };
  tag: string[];
}) => client.post('global-glossary', { json }).json<GlobalGlossary>();

const updateGlobalGlossary = (
  id: string,
  json: {
    name: string;
    content: { [key: string]: string };
    tag: string[];
  },
) => client.put(`global-glossary/${id}`, { json }).json<GlobalGlossary>();

const deleteGlobalGlossary = (id: string) =>
  client.delete(`global-glossary/${id}`);

const deleteGlobalGlossaryRecord = (id: string, index: number) =>
  client.delete(`global-glossary/${id}/record/${index}`);

export const GlobalGlossaryApi = {
  listGlobalGlossaries,
  getGlobalGlossary,
  createGlobalGlossary,
  updateGlobalGlossary,
  deleteGlobalGlossary,
  deleteGlobalGlossaryRecord,
};
